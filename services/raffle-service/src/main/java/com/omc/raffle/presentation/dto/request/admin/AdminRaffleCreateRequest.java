package com.omc.raffle.presentation.dto.request.admin;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminRaffleCreateRequest(
        @NotNull UUID dropId,
        @NotNull UUID productId,
        @NotBlank String name,
        @Positive int winnerCount,
        @NotNull @Future LocalDateTime startedAt,
        @NotNull @Future LocalDateTime endedAt
) {
}
