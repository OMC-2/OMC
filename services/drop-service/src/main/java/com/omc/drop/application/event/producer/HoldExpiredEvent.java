package com.omc.drop.application.event.producer;

import com.omc.common.util.UuidV7Generator;
import java.util.UUID;

public record HoldExpiredEvent(
        String eventId,
        UUID orderId,
        UUID dropId
) {
    public static HoldExpiredEvent of(UUID orderId, UUID dropId) {
        return new HoldExpiredEvent(UuidV7Generator.generate().toString(), orderId, dropId);
    }
}
