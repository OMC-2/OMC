package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record StockFailedEvent(
    String eventId,
    UUID orderId,
    UUID productId,
    UUID dropId,
    UUID userId
) {}
