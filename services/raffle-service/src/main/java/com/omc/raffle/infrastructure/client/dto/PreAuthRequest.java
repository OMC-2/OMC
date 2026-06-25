package com.omc.raffle.infrastructure.client.dto;

import java.math.BigDecimal;

public record PreAuthRequest(
        String billingKeyId,
        BigDecimal amount
) {}
