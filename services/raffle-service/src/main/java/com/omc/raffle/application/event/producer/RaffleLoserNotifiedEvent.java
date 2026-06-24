package com.omc.raffle.application.event.producer;

import java.util.UUID;

/**
 * 래플 미당첨 시 알림 서버로 전달되는 이벤트를 위한 DTO.
 */
public record RaffleLoserNotifiedEvent(
        String eventId,
        UUID raffleId,
        UUID userId
) {
}
