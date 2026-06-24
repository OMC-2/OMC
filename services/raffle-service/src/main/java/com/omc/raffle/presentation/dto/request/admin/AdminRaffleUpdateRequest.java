package com.omc.raffle.presentation.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record AdminRaffleUpdateRequest(
        @NotBlank String name,
        @Positive int winnerCount
) {
}
