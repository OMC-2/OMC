package com.omc.order.application.event.dto;

import java.util.UUID;

//payment가 PG 취소 완료 후 발행하는 refund.done 수신 DTO
public record RefundDoneEvent(
    String eventId,
    UUID orderId,
    UUID userId,
    UUID couponId,
    Long amount,
    String refundReason
) {}
