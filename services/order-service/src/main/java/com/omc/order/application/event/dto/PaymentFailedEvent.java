package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PaymentFailedEvent(
    String eventId,
    String salesType,
    UUID userId,
    String failureReason,
    UUID orderId
) {}
