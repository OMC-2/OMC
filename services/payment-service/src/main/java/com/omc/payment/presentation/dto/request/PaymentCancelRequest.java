package com.omc.payment.presentation.dto.request;

import com.omc.payment.domain.enums.CancellationCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentCancelRequest(
        @NotNull CancellationCode cancellationCode,
        @NotBlank String cancelReason
) {
}
