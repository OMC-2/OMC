package com.omc.payment.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ConfirmPaymentRequest(
        @NotNull UUID orderID,
        UUID couponID,
        @NotNull @PositiveOrZero Long originalAmount
) {
}
