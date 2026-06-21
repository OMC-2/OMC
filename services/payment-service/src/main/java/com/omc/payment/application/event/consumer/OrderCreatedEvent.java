package com.omc.payment.application.event.consumer;

import java.util.UUID;

public record OrderCreatedEvent(
        String eventId,
        UUID orderId,
        UUID userId,
        String orderType,
        UUID dropId,
        UUID raffleId,
        UUID entryId,
        Long originalAmount,
        Long discountAmount,
        Long finalAmount,
        UUID couponId,
        String billingKeyId
) {
}
