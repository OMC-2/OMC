package com.omc.product.presentation.dto.request;

import com.omc.product.domain.enums.ProductStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @Size(max = 100) String name,
        String description,
        @Min(1) Long price,
        String imageUrl,
        ProductStatus status
) {}