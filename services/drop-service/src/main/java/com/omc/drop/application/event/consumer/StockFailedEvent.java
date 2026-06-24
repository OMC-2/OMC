package com.omc.drop.application.event.consumer;

import java.util.UUID;

public record StockFailedEvent(
        String eventId,
        UUID orderId,
        UUID productId,
        UUID dropId,
        UUID userId
) {
}
