package com.omc.raffle.presentation.dto.request.admin;

import com.omc.raffle.domain.enums.RaffleStatus;
import jakarta.validation.constraints.NotNull;

public record AdminRaffleStatusUpdateRequest(
        @NotNull RaffleStatus status
) {
}
