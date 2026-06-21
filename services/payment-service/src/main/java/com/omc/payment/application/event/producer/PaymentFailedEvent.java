package com.omc.payment.application.event.producer;

import java.util.UUID;

public record PaymentFailedEvent(
        UUID dropId,
        String eventId,
        UUID orderId,
        UUID userId,
        String failureReason
) {
}
