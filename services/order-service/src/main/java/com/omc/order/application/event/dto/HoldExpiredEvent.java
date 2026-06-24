package com.omc.order.application.event.dto;

import lombok.Builder;

import java.util.UUID;

//drop 발행, userId는 협의에 따라 제거됨
@Builder
public record HoldExpiredEvent(
    String eventId,
    UUID orderId,
    UUID dropId
) {}
