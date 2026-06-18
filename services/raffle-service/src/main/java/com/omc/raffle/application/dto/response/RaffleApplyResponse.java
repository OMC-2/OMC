package com.omc.raffle.application.dto.response;

import com.omc.raffle.domain.entity.RaffleEntry;
import java.time.LocalDateTime;
import java.util.UUID;

public record RaffleApplyResponse(
        UUID entryId,
        UUID raffleId,
        UUID userId,
        LocalDateTime enteredAt
) {
    public static RaffleApplyResponse from(RaffleEntry entry) {
        return new RaffleApplyResponse(
                entry.getId(),
                entry.getRaffleId(),
                entry.getUserId(),
                entry.getEnteredAt()
        );
    }
}
