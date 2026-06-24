package com.omc.raffle.presentation.dto.response;

import com.omc.raffle.domain.entity.Raffle;
import com.omc.raffle.domain.enums.RaffleStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record RaffleResponse(
        UUID id,
        UUID dropId,
        String name,
        int winnerCount,
        RaffleStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
    public static RaffleResponse from(Raffle raffle) {
        return new RaffleResponse(
                raffle.getId(),
                raffle.getDropId(),
                raffle.getName(),
                raffle.getWinnerCount(),
                raffle.getStatus(),
                raffle.getStartedAt(),
                raffle.getEndedAt(),
                raffle.getCreatedAt()
        );
    }
}
