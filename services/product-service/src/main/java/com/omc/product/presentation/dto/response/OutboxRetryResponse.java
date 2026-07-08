package com.omc.product.presentation.dto.response;

public record OutboxRetryResponse(
        int retriedCount
) {}
