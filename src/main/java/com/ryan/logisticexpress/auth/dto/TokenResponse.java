package com.ryan.logisticexpress.auth.dto;

import java.util.List;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        List<String> roles,
        String redirectUrl
) {
    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn, List<String> roles, String redirectUrl) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, roles, redirectUrl);
    }
}
