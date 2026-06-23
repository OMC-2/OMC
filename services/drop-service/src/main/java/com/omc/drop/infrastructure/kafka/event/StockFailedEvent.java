package com.omc.drop.infrastructure.kafka.event;

import java.util.UUID;

public record StockFailedEvent(
        String eventId,
        UUID orderId,
        UUID productId,
        UUID dropId,
        UUID userId
) {
}
