package com.omc.order.application.event.dto;

import lombok.Builder;
import org.springframework.retry.annotation.Recover;

import java.util.UUID;

@Builder
public record OrderCancelledEvent(
    String eventId,
    UUID orderId,
    UUID userId,
    UUID raffleId,
    UUID entryId,
    String reason
) {}
