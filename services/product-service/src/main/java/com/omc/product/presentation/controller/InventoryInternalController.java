package com.omc.product.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.service.InventoryService;
import com.omc.product.presentation.dto.response.InventorySnapshotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Inventory Internal", description = "재고 내부 API (서비스 간 통신 전용)")
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class InventoryInternalController {

    private final InventoryService inventoryService;

    @Operation(summary = "재고 스냅샷 조회", description = "드롭 오픈 시 Drop Service가 Redis 워밍을 위해 호출합니다.")
    @GetMapping("/{productId}/inventories/snapshot")
    public ResponseEntity<ApiResponse<InventorySnapshotResponse>> getSnapshot(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getSnapshot(productId)));
    }
}