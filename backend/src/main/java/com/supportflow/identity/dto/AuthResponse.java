package com.supportflow.identity.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInMs,
        UserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresInMs, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs, user);
    }
}
