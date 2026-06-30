package com.omc.raffle.presentation.dto.request;

import java.math.BigDecimal;

public record PreAuthRequest(
        String billingKeyId,
        BigDecimal amount
) {}
