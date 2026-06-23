package com.omc.drop.infrastructure.kafka.event;

import java.util.UUID;

public record PaymentCompletedEvent(
        String eventId,
        String salesType,
        UUID userId,
        UUID couponId,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        UUID orderId,
        UUID dropId
) {
}
