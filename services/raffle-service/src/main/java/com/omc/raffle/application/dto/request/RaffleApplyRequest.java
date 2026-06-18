package com.omc.raffle.application.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RaffleApplyRequest(
        @NotNull(message = "사용자 ID는 필수입니다.") UUID userId,
        @NotNull(message = "결제 수단(빌링키) ID는 필수입니다.") UUID billingKeyId
) {
}
