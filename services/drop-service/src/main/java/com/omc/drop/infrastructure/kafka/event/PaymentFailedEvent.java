package com.omc.drop.infrastructure.kafka.event;

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
