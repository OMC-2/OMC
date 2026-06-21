package com.omc.raffle.application.event.consumer;

import java.util.UUID;

/**
 * 결제 서버에서 발생하는 결제 실패 이벤트를 수신하기 위한 DTO
 */
public record PaymentFailedEvent(
        UUID eventId,
        String salesType,
        UUID userId,
        String failureReason,
        UUID orderId,
        UUID dropId
) {
}
