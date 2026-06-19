package com.omc.product.presentation.dto.response;

import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.enums.ProductStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID productId,
        String name,
        String description,
        String brand,
        String category,
        Long price,
        String imageUrl,
        ProductStatus status,
        Integer availableQuantity,
        LocalDateTime createdAt
) {
    public static ProductResponse of(Product product, Inventory inventory) {
        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getCategory(),
                product.getPrice(),
                product.getImageUrl(),
                product.getStatus(),
                inventory != null ? inventory.getAvailableQuantity() : null,
                product.getCreatedAt()
        );
    }
}