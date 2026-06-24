package com.omc.raffle.presentation.dto.response;

import com.omc.raffle.domain.entity.RaffleEntry;

import java.time.LocalDateTime;
import java.util.UUID;

public record RaffleEntryResponse(
        UUID id,
        UUID raffleId,
        UUID userId,
        String billingKeyId,
        UUID couponId,
        java.math.BigDecimal originalAmount,
        java.math.BigDecimal discountAmount,
        java.math.BigDecimal finalAmount,
        LocalDateTime enteredAt
) {
    public static RaffleEntryResponse from(RaffleEntry entry) {
        return new RaffleEntryResponse(
                entry.getId(),
                entry.getRaffleId(),
                entry.getUserId(),
                entry.getBillingKeyId(),
                entry.getCouponId(),
                entry.getOriginalAmount(),
                entry.getDiscountAmount(),
                entry.getFinalAmount(),
                entry.getEnteredAt()
        );
    }
}
