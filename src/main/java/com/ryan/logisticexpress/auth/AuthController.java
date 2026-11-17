package com.ryan.logisticexpress.auth;

import com.ryan.logisticexpress.auth.dto.*;
import com.ryan.logisticexpress.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieCsrfTokenRepository csrfTokenRepository;

    @Autowired
    public AuthController(
            AuthService authService,
            @Autowired(required = false) CookieCsrfTokenRepository csrfTokenRepository
    ) {
        this.authService = authService;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    public AuthController(AuthService authService) {
        this(authService, null);
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        TokenResponse response = authService.registerShop(request, resolveClientIp(httpRequest), userAgent);
        ResponseCookie accessCookie = authService.createAuthCookie(response.accessToken());
        ResponseCookie refreshCookie = authService.createRefreshTokenCookie(response.refreshToken());
        rotateCsrfToken(httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        TokenResponse response = authService.login(request, resolveClientIp(httpRequest), userAgent);
        ResponseCookie accessCookie = authService.createAuthCookie(response.accessToken());
        ResponseCookie refreshCookie = authService.createRefreshTokenCookie(response.refreshToken());
        rotateCsrfToken(httpRequest, httpResponse);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "REFRESH_TOKEN", required = false) String cookieRefreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String token = StringUtils.hasText(cookieRefreshToken)
                ? cookieRefreshToken
                : (request != null ? request.refreshToken() : null);
        authService.logout(token);
        ResponseCookie clearAccessCookie = authService.createClearCookie();
        ResponseCookie clearRefreshCookie = authService.createClearRefreshTokenCookie();
        rotateCsrfToken(httpRequest, httpResponse);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString(), clearRefreshCookie.toString())
                .body(Map.of("status", "LOGGED_OUT", "message", "Logged out successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "REFRESH_TOKEN", required = false) String cookieRefreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        String token = StringUtils.hasText(cookieRefreshToken)
                ? cookieRefreshToken
                : (request != null ? request.refreshToken() : null);
        TokenResponse response = authService.refreshToken(token, resolveClientIp(httpRequest), userAgent);
        ResponseCookie accessCookie = authService.createAuthCookie(response.accessToken());
        ResponseCookie refreshCookie = authService.createRefreshTokenCookie(response.refreshToken());
        rotateCsrfToken(httpRequest, httpResponse);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                .body(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        authService.forgotPassword(request, resolveClientIp(httpRequest));
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "If the email exists, password reset instructions have been sent"
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        ResponseCookie clearAccessCookie = authService.createClearCookie();
        ResponseCookie clearRefreshCookie = authService.createClearRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString(), clearRefreshCookie.toString())
                .body(Map.of("status", "OK", "message", "Password has been successfully reset"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUserId, request);
        ResponseCookie clearAccessCookie = authService.createClearCookie();
        ResponseCookie clearRefreshCookie = authService.createClearRefreshTokenCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString(), clearRefreshCookie.toString())
                .body(Map.of("status", "OK", "message", "Password changed successfully"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request != null ? request.getRemoteAddr() : null;
    }

    private void rotateCsrfToken(HttpServletRequest request, HttpServletResponse response) {
        if (csrfTokenRepository != null && request != null && response != null) {
            CsrfToken newToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(newToken, request, response);
            request.setAttribute(CsrfToken.class.getName(), newToken);
            request.setAttribute(newToken.getParameterName(), newToken);
        }
    }
}
