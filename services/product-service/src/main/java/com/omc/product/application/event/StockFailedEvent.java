package com.omc.product.application.event;

import java.util.UUID;

public record StockFailedEvent(
        String eventId,
        UUID orderId,
        UUID productId,
        UUID dropId,
        UUID userId
) {}