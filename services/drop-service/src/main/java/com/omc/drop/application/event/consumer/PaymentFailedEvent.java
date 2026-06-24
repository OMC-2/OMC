package com.omc.drop.application.event.consumer;

import java.util.UUID;

public record PaymentFailedEvent(
        String eventId,
        String salesType,
        UUID userId,
        String failureReason,
        UUID orderId,
        UUID dropId
) {
}
