package com.omc.user.infrastructure.client;

public record KeycloakTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {}
