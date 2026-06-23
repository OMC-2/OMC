package com.omc.order.infrastructure.client.dto;

public record ProductResponse(
    Long price //Order의 originalAmount로 사용
) {}
