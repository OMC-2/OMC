package com.omc.raffle.presentation.dto.response;

import com.omc.raffle.domain.enums.RaffleResultStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record RaffleResultResponse(
        UUID resultId,
        UUID raffleId,
        UUID userId,
        RaffleResultStatus status,
        LocalDateTime decidedAt
) {
}
