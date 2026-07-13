package com.omc.drop.application.event.producer;

import java.time.LocalDateTime;
import java.util.UUID;

public record PurchaseConfirmedEvent(
        String eventId,
        UUID orderId,
        UUID dropId,
        UUID userId,
        UUID productId,
        LocalDateTime holdExpiresAt
) {
}
