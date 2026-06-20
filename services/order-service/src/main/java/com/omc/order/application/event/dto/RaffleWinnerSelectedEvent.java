package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record RaffleWinnerSelectedEvent(
    String eventId,
    UUID entryId,
    UUID raffleId,
    UUID userId,
    UUID productId,
    UUID billingKeyId,
    UUID couponId,
    Long originalAmount,
    Long discountAmount,
    Long finalAmount
) {}
