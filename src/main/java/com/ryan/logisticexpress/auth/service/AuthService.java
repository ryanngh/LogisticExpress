package com.ryan.logisticexpress.auth.service;

import com.ryan.logisticexpress.auth.dto.*;
import com.ryan.logisticexpress.auth.entity.UserSession;
import com.ryan.logisticexpress.auth.entity.VerificationToken;
import com.ryan.logisticexpress.auth.exceptions.AccountAlreadyExistsException;
import com.ryan.logisticexpress.auth.exceptions.InvalidCredentialsException;
import com.ryan.logisticexpress.auth.exceptions.InvalidRefreshTokenException;
import com.ryan.logisticexpress.auth.repository.UserSessionRepository;
import com.ryan.logisticexpress.auth.repository.VerificationTokenRepository;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ForbiddenException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.security.CustomUserDetailsService;
import com.ryan.logisticexpress.security.JwtService;
import com.ryan.logisticexpress.user.entity.ShopProfile;
import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.entity.UserCredential;
import com.ryan.logisticexpress.user.entity.UserProfile;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.repository.UserRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    @Setter
    private boolean cookieSecure;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            VerificationTokenRepository verificationTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Autowired(required = false) CustomUserDetailsService userDetailsService,
            @Value("${app.cookie.secure:false}") boolean cookieSecure
    ) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.cookieSecure = cookieSecure;
    }

    public AuthService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            VerificationTokenRepository verificationTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this(userRepository, userSessionRepository, verificationTokenRepository, passwordEncoder, jwtService, null, false);
    }

    // Register a new customer/shop account and owner profile
    @Transactional
    public TokenResponse registerShop(RegisterRequest request, String ipAddress, String userAgent) {
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();
        String fullName = request.fullName().trim();
        String shopName = StringUtils.hasText(request.shopName()) ? request.shopName().trim() : fullName;

        if (userRepository.existsByEmail(email)) {
            throw new AccountAlreadyExistsException("Email already in use: " + email);
        }
        if (userRepository.existsByPhone(phone)) {
            throw new AccountAlreadyExistsException("Phone number already in use: " + phone);
        }

        String username = resolveOrGenerateUsername(request.username(), email);
        if (userRepository.existsByUsername(username)) {
            throw new AccountAlreadyExistsException("Username already in use: " + username);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .status(Status.ACTIVE)
                .roles(Set.of(Role.ROLE_SHOP))
                .build();

        UserProfile profile = UserProfile.builder()
                .user(user)
                .fullName(fullName)
                .phone(phone)
                .build();
        user.setProfile(profile);

        UserCredential credential = UserCredential.builder()
                .user(user)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        user.setCredential(credential);

        ShopProfile shopProfile = ShopProfile.builder()
                .user(user)
                .shopName(shopName)
                .debtBalance(BigDecimal.ZERO)
                .build();
        user.setShopProfile(shopProfile);

        User savedUser = userRepository.save(user);
        return createSessionAndGenerateTokens(savedUser, ipAddress, userAgent);
    }

    // Authenticate user credentials and create a session
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String identifier = request.usernameOrEmail().trim();

        User user = userRepository.findByIdentifierWithDetails(identifier.toLowerCase(), identifier)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (user.getStatus() == Status.DISABLED) {
            throw new ForbiddenException("ACCOUNT_DISABLED", "Account has been disabled");
        }

        UserCredential credential = user.getCredential();
        if (credential == null) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        if (credential.isAccountLocked()) {
            throw new ForbiddenException("ACCOUNT_LOCKED", "Account is locked until " + credential.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            credential.recordFailedLogin(5, 900);
            userRepository.save(user);
            if (credential.isAccountLocked()) {
                evictUserCache(user.getId());
            }
            throw new InvalidCredentialsException("Invalid credentials");
        }

        credential.resetFailedLogin();
        user.recordLogin();
        userRepository.save(user);
        if (userDetailsService != null) {
            userDetailsService.cacheUserAuthState(user);
        }

        return createSessionAndGenerateTokens(user, ipAddress, userAgent);
    }

    // Revoke current session
    @Transactional
    public void logout(String refreshToken) {
        if (StringUtils.hasText(refreshToken)) {
            userSessionRepository.revokeByRefreshTokenHash(
                    hashSha256(refreshToken.trim()),
                    UserSession.REASON_LOGOUT,
                    Instant.now()
            );
        }
    }

    // Rotate refresh token and issue a new access token
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refreshToken(String refreshToken, String ipAddress, String userAgent) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }

        String hash = hashSha256(refreshToken.trim());
        UserSession session = userSessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));

        if (session.isRevoked()) {
            userSessionRepository.revokeFamily(session.getFamilyId(), UserSession.REASON_REUSE_DETECTED, Instant.now());
            throw new InvalidRefreshTokenException("Refresh token reuse detected. Family sessions revoked.");
        }

        if (session.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        User user = session.getUser();
        if (user.getStatus() == Status.DISABLED) {
            throw new ForbiddenException("ACCOUNT_DISABLED", "Account has been disabled");
        }

        session.setRotatedAt(Instant.now());
        session.revoke(UserSession.REASON_LOGOUT);
        userSessionRepository.save(session);

        String newRawRefreshToken = generateRawToken();
        String newHash = hashSha256(newRawRefreshToken);

        UserSession newSession = UserSession.builder()
                .user(user)
                .familyId(session.getFamilyId())
                .refreshTokenHash(newHash)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        userSessionRepository.save(newSession);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtService.generateToken(userDetails);
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return TokenResponse.of(
                newAccessToken,
                newRawRefreshToken,
                jwtService.getExpirationMs() / 1000,
                roles,
                determineRedirectUrl(user.getRoles())
        );
    }

    // Generate password reset token
    @Transactional
    public String forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        String email = request.email().trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existing email: {}", email);
            return null;
        }

        User user = userOpt.get();
        verificationTokenRepository.consumeActiveTokens(user.getId(), VerificationToken.PURPOSE_PASSWORD_RESET, Instant.now());

        String rawToken = generateRawToken();
        String tokenHash = hashSha256(rawToken);

        VerificationToken token = VerificationToken.builder()
                .user(user)
                .purpose(VerificationToken.PURPOSE_PASSWORD_RESET)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(Duration.ofHours(2)))
                .requestIp(ipAddress)
                .build();
        verificationTokenRepository.save(token);

        return rawToken;
    }

    // Reset password using verification token
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hashSha256(request.token().trim());
        VerificationToken token = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("INVALID_TOKEN", "Reset token is invalid or does not exist"));

        if (!token.isValid() || !VerificationToken.PURPOSE_PASSWORD_RESET.equals(token.getPurpose())) {
            throw new BadRequestException("INVALID_TOKEN", "Reset token is invalid, expired, or already used");
        }

        User user = token.getUser();
        UserCredential credential = user.getCredential();
        if (credential == null) {
            throw new BadRequestException("NO_CREDENTIALS", "User has no credentials configured");
        }

        credential.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        credential.setPasswordChangedAt(Instant.now());
        credential.setMustChangePassword(false);
        credential.resetFailedLogin();

        token.consume();
        verificationTokenRepository.save(token);

        user.incrementTokenVersion();
        userSessionRepository.revokeAllForUser(user.getId(), UserSession.REASON_PASSWORD_CHANGED, Instant.now());
        userRepository.save(user);
        evictUserCache(user.getId());
    }

    // Change password with old password verification
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByIdWithDetails(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        UserCredential credential = user.getCredential();
        if (credential == null || !passwordEncoder.matches(request.currentPassword(), credential.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is not correct");
        }

        credential.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        credential.setPasswordChangedAt(Instant.now());
        credential.setMustChangePassword(false);

        user.incrementTokenVersion();
        userSessionRepository.revokeAllForUser(user.getId(), UserSession.REASON_PASSWORD_CHANGED, Instant.now());
        userRepository.save(user);
        evictUserCache(user.getId());
    }

    public ResponseCookie createAuthCookie(String token) {
        String cookieName = StringUtils.hasText(jwtService.getCookieName()) ? jwtService.getCookieName() : "ACCESS_TOKEN";
        long maxAgeMs = jwtService.getExpirationMs() > 0 ? jwtService.getExpirationMs() : 900000L;
        return ResponseCookie.from(cookieName, token != null ? token : "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ofMillis(maxAgeMs))
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        String cookieName = StringUtils.hasText(jwtService.getRefreshCookieName()) ? jwtService.getRefreshCookieName() : "REFRESH_TOKEN";
        long maxAgeMs = jwtService.getRefreshExpirationMs() > 0 ? jwtService.getRefreshExpirationMs() : 604800000L;
        return ResponseCookie.from(cookieName, refreshToken != null ? refreshToken : "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/auth")
                .maxAge(Duration.ofMillis(maxAgeMs))
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie createClearCookie() {
        String cookieName = StringUtils.hasText(jwtService.getCookieName()) ? jwtService.getCookieName() : "ACCESS_TOKEN";
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie createClearRefreshTokenCookie() {
        String cookieName = StringUtils.hasText(jwtService.getRefreshCookieName()) ? jwtService.getRefreshCookieName() : "REFRESH_TOKEN";
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    private void evictUserCache(UUID userId) {
        if (userDetailsService != null && userId != null) {
            userDetailsService.evictUserAuthState(userId);
        }
    }

    private TokenResponse createSessionAndGenerateTokens(User user, String ipAddress, String userAgent) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtService.generateToken(userDetails);
        String rawRefreshToken = generateRawToken();
        String refreshTokenHash = hashSha256(rawRefreshToken);

        UserSession session = UserSession.builder()
                .user(user)
                .familyId(UUID.randomUUID())
                .refreshTokenHash(refreshTokenHash)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        userSessionRepository.save(session);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return TokenResponse.of(
                accessToken,
                rawRefreshToken,
                jwtService.getExpirationMs() / 1000,
                roles,
                determineRedirectUrl(user.getRoles())
        );
    }

    public String determineRedirectUrl(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) return "/";
        if (roles.contains(Role.ROLE_SHOP)) return "/shop/dashboard";
        if (roles.contains(Role.ROLE_SHIPPER)) return "/shipper/tasks";
        return "/ops/dashboard";
    }

    private String resolveOrGenerateUsername(String username, String email) {
        if (StringUtils.hasText(username)) return username.trim().toLowerCase();
        String base = email.split("@")[0].replaceAll("[^a-z0-9_.]", "");
        if (base.length() < 3) base = "shop_" + base;
        if (base.length() > 40) base = base.substring(0, 40);
        String candidate = base;
        int count = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + count;
            count++;
        }
        return candidate;
    }

    public static String hashSha256(String raw) {
        if (raw == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
