package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderShippedEvent(
    String eventId,
    UUID orderId,
    UUID userId
) {}
