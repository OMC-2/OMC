package com.omc.payment.application.event.producer;

import java.util.UUID;

public record RefundDoneEvent(
        String eventId,
        UUID orderId,
        UUID userId,
        Long amount,
        String refundReason
) {
}
