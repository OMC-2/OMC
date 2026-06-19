package com.omc.drop.infrastructure.kafka.event;

import java.util.UUID;

public record RefundRequestedEvent(
        String eventId,
        UUID orderId,
        UUID userId,
        String reason
) {
    public static RefundRequestedEvent of(UUID orderId, UUID userId, String reason) {
        return new RefundRequestedEvent(UUID.randomUUID().toString(), orderId, userId, reason);
    }
}
