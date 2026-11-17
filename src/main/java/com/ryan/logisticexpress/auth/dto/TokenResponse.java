package com.ryan.logisticexpress.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;

public record TokenResponse(
        @JsonIgnore String accessToken,
        @JsonIgnore String refreshToken,
        String status,
        long expiresIn,
        List<String> roles,
        String redirectUrl,
        Map<String, Object> user
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, List<String> roles, String redirectUrl) {
        List<String> safeRoles = roles != null ? roles : List.of();
        String safeRedirect = redirectUrl != null ? redirectUrl : "/";
        return new TokenResponse(
                accessToken,
                refreshToken,
                "AUTHENTICATED",
                expiresIn,
                safeRoles,
                safeRedirect,
                Map.of("roles", safeRoles, "redirectUrl", safeRedirect)
        );
    }
}
