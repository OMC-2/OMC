package com.omc.raffle.presentation.dto.response;

import com.omc.raffle.domain.enums.RaffleResultStatus;
import java.util.UUID;
import java.time.LocalDateTime;

public record PublicRaffleResultResponse(
        UUID userId,
        RaffleResultStatus result,
        LocalDateTime decidedAt
) {
}
