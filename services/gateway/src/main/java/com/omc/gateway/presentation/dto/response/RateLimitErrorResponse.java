package com.omc.gateway.presentation.dto.response;

public record RateLimitErrorResponse(
    boolean success,
    int status,
    String errorCode,
    String message
) {
}
