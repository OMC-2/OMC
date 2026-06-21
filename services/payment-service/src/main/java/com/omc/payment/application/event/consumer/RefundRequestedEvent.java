package com.omc.payment.application.event.consumer;

import java.util.UUID;

public record RefundRequestedEvent(
        String eventId,
        UUID orderId,
        UUID userId,
        String reason
) {
}
