package com.omc.product.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InventoryUpdateRequest(
        @NotNull @Min(0) Integer totalQuantity,
        @NotBlank String reason  // 감사 로그용
) {}