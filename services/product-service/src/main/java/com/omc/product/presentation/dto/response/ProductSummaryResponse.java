package com.omc.product.presentation.dto.response;

import com.omc.product.domain.entity.Product;
import com.omc.product.domain.enums.ProductStatus;

import java.util.UUID;

public record ProductSummaryResponse(
        UUID productId,
        String name,
        String brand,
        String category,
        Long price,
        String imageUrl,
        ProductStatus status
) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getProductId(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getPrice(),
                product.getImageUrl(),
                product.getStatus()
        );
    }
}