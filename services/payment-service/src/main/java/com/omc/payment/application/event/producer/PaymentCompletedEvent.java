package com.omc.payment.application.event.producer;

import java.util.UUID;

public record PaymentCompletedEvent(
        UUID dropId,
        String eventId,
        UUID orderId,
        UUID userId,
        UUID couponId,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount
) {
}
