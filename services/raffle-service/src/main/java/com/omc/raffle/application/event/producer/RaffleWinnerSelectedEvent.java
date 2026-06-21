package com.omc.raffle.application.event.producer;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 래플 당첨 시 결제 및 주문 서버로 전달되는 이벤트를 위한 DTO.
 * 주문 확정을 위한 가격 정보 및 유저 정보를 담고 있습니다.
 */
public record RaffleWinnerSelectedEvent(
        UUID raffleId,
        UUID entryId,
        UUID userId,
        UUID billingKeyId,
        UUID couponId,
        java.math.BigDecimal originalAmount,
        java.math.BigDecimal discountAmount,
        java.math.BigDecimal finalAmount,
        LocalDateTime selectedAt
) {
}
