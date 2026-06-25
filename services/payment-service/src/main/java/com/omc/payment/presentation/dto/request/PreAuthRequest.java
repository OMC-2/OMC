package com.omc.payment.presentation.dto.request;

import java.math.BigDecimal;

public record PreAuthRequest(
        String billingKeyId,
        BigDecimal amount
) {}
