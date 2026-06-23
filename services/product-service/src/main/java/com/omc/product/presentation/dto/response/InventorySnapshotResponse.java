package com.omc.product.presentation.dto.response;

import com.omc.product.domain.entity.Inventory;

import java.util.UUID;


/**
 * Drop Service용 Internal API 응답
 * totalQuantity만 반환 (reservedQuantity는 Drop Service가 Redis에서 직접 관리)
 */
public record InventorySnapshotResponse(
        UUID productId,
        int totalQuantity,
        int soldQuantity,
        int availableQuantity
) {
    public static InventorySnapshotResponse from(Inventory inventory) {
        return new InventorySnapshotResponse(
                inventory.getProductId(),
                inventory.getTotalQuantity(),
                inventory.getSoldQuantity(),
                inventory.getAvailableQuantity()
        );
    }
}