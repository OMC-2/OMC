package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderCreatedEvent(
    String eventId,
    UUID orderId,
    UUID userId,
    String orderType,
    UUID dropId,
    UUID raffleId,
    UUID entryId,
    Long finalAmount,
    UUID couponId,
    UUID billingKeyId
) {}
