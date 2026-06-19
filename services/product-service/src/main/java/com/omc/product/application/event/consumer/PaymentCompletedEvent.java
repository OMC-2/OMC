package com.omc.product.application.event.consumer;

import java.util.UUID;

public record PaymentCompletedEvent(
        String eventId,     // 멱등성 키
        UUID orderId,
        UUID productId,
        UUID userId,
        int quantity,
        long amount
) {}