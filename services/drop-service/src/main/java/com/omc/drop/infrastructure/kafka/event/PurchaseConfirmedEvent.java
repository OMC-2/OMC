package com.omc.drop.infrastructure.kafka.event;

import com.omc.common.util.UuidV7Generator;
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
                UuidV7Generator.generate().toString(),
                orderId,
                dropId,
                userId,
                productId,
                LocalDateTime.now().plusSeconds(holdTtlSec)
        );
    }
}
