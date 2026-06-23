package com.omc.drop.infrastructure.kafka.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseConfirmedEvent(
        String eventId,
        UUID orderId,
        UUID dropId,
        UUID userId,
        UUID productId,
        LocalDateTime holdExpiresAt
) {
    public static PurchaseConfirmedEvent of(UUID orderId, UUID dropId, UUID userId, UUID productId, int holdTtlSec) {
        return new PurchaseConfirmedEvent(
                UUID.randomUUID().toString(),
                orderId,
                dropId,
                userId,
                productId,
                LocalDateTime.now().plusSeconds(holdTtlSec)
        );
    }
}
