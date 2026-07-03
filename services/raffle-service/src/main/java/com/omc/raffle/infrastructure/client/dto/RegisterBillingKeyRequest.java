package com.omc.raffle.infrastructure.client.dto;

public record RegisterBillingKeyRequest(
        String customerKey,
        String authKey
) {}
