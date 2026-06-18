package com.omc.raffle.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RaffleEnterRequest(
        @NotNull(message = "결제 수단(빌링키) ID는 필수입니다.") UUID billingKeyId
) {
}
