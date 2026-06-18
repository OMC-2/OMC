package com.omc.product.presentation.dto.response;

import com.omc.product.domain.entity.Inventory;

import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryResponse(
        UUID inventoryId,
        UUID productId,
        int totalQuantity,
        int soldQuantity,
        int availableQuantity,
        LocalDateTime updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getProductId(),
                inventory.getTotalQuantity(),
                inventory.getSoldQuantity(),
                inventory.getAvailableQuantity(),
                inventory.getUpdatedAt()
        );
    }
}