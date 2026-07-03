package com.omc.raffle.infrastructure.client.dto;

public record PreAuthRequest(
        String billingKeyId,
        Long amount
) {}
