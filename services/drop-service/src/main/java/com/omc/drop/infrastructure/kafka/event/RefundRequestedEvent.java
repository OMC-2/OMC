package com.omc.drop.infrastructure.kafka.event;

import com.omc.common.util.UuidV7Generator;
import java.util.UUID;

public record RefundRequestedEvent(
        String eventId,
        UUID orderId,
        UUID userId,
        String reason
) {
    public static RefundRequestedEvent of(UUID orderId, UUID userId, String reason) {
        return new RefundRequestedEvent(UuidV7Generator.generate().toString(), orderId, userId, reason);
    }
}
