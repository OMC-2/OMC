package com.omc.product.application.event;

import java.util.UUID;

public record PaymentCompletedEvent(
        String eventId,
        UUID orderId,
        UUID productId,
        UUID userId,
        UUID dropId,
        int quantity,
        long amount
) {}