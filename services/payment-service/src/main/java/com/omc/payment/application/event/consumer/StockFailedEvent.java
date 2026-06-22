package com.omc.payment.application.event.consumer;

import java.util.UUID;

public record StockFailedEvent(
        String eventId,
        UUID orderId
) {
}
