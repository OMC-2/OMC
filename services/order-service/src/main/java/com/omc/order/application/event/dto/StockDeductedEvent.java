package com.omc.order.application.event.dto;

import java.util.UUID;

public record StockDeductedEvent(
    String eventId,
    UUID orderId,
    UUID productId
) {}
