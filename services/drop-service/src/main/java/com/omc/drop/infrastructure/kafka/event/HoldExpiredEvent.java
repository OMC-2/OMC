package com.omc.drop.infrastructure.kafka.event;

import java.util.UUID;

public record HoldExpiredEvent(
        String eventId,
        UUID orderId,
        UUID dropId
) {
    public static HoldExpiredEvent of(UUID orderId, UUID dropId) {
        return new HoldExpiredEvent(UUID.randomUUID().toString(), orderId, dropId);
    }
}
