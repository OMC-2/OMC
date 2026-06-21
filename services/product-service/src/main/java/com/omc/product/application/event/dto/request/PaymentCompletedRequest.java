package com.omc.product.application.event.dto.request;

import java.util.UUID;

public record PaymentCompletedRequest(
        String eventId,     // 멱등성 키
        UUID orderId,
        UUID productId,
        UUID userId,
        UUID dropId,
        int quantity,
        long amount
) {}