package com.omc.product.application;

import java.util.UUID;

public record StockDeductedEvent(
        String eventId,
        UUID orderId,
        UUID productId,
        UUID userId,
        UUID dropId
) {}
