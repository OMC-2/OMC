package com.omc.raffle.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RaffleEnterRequest(
        @NotNull(message = "결제 수단(빌링키) ID는 필수입니다.") UUID billingKeyId,
        UUID couponId,
        @NotNull(message = "원래 금액은 필수입니다.") java.math.BigDecimal originalAmount,
        @NotNull(message = "할인 금액은 필수입니다.") java.math.BigDecimal discountAmount,
        @NotNull(message = "최종 결제 금액은 필수입니다.") java.math.BigDecimal finalAmount
) {
}
