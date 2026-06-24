package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderConfirmedEvent(
    String eventId,
    UUID orderId,
    UUID userId
) {}
