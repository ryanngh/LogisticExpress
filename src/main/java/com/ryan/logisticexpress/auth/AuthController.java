package com.ryan.logisticexpress.auth;

import com.ryan.logisticexpress.auth.dto.*;
import com.ryan.logisticexpress.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        TokenResponse response = authService.registerShop(request, ipAddress, userAgent);
        ResponseCookie cookie = authService.createAuthCookie(response.accessToken());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        TokenResponse response = authService.login(request, ipAddress, userAgent);
        ResponseCookie cookie = authService.createAuthCookie(response.accessToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "REFRESH_TOKEN", required = false) String cookieRefreshToken
    ) {
        String token = (request != null && StringUtils.hasText(request.refreshToken()))
                ? request.refreshToken()
                : cookieRefreshToken;
        authService.logout(token);
        ResponseCookie clearCookie = authService.createClearCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "REFRESH_TOKEN", required = false) String cookieRefreshToken,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
            @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        String token = (request != null && StringUtils.hasText(request.refreshToken()))
                ? request.refreshToken()
                : cookieRefreshToken;
        TokenResponse response = authService.refreshToken(token, ipAddress, userAgent);
        ResponseCookie cookie = authService.createAuthCookie(response.accessToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress
    ) {
        String resetToken = authService.forgotPassword(request, ipAddress);
        if (resetToken != null) {
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset instructions have been generated",
                    "resetToken", resetToken
            ));
        }
        return ResponseEntity.ok(Map.of("message", "If the email exists, password reset instructions have been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UUID currentUserId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(currentUserId, request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
