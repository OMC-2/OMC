package com.omc.raffle.application.dto.response;

import com.omc.raffle.domain.entity.RaffleEntry;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record RaffleApplyResponse(
        UUID entryId,
        UUID dropId,
        UUID userId,
        LocalDateTime enteredAt
) {
    public static RaffleApplyResponse from(RaffleEntry entry) {
        return RaffleApplyResponse.builder()
                .entryId(entry.getId())
                .dropId(entry.getDropId())
                .userId(entry.getUserId())
                .enteredAt(entry.getEnteredAt())
                .build();
    }
}
