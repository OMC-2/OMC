package com.omc.raffle.presentation.dto.response;

import com.omc.raffle.domain.entity.RaffleEntry;
import java.time.LocalDateTime;
import java.util.UUID;

public record RaffleApplyResponse(
        UUID entryId,
        UUID raffleId,
        UUID userId,
        String billingKeyId,
        UUID couponId,
        java.math.BigDecimal originalAmount,
        java.math.BigDecimal discountAmount,
        java.math.BigDecimal finalAmount,
        LocalDateTime enteredAt
) {
}

