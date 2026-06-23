package com.omc.product.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @NotNull @Min(1) Long price,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 30) String category,
        String imageUrl,
        @NotNull @Min(1) Integer initialQuantity
) {}