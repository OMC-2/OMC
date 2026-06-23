package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record PurchaseConfirmedEvent(
    String eventId,
    UUID orderId,
    UUID dropId,
    UUID userId,
    UUID productId,
    String holdExpiresAt
) {}
