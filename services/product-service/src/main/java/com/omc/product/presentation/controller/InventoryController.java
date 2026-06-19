package com.omc.product.presentation.controller;

import com.omc.product.application.service.InventoryService;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import com.omc.product.presentation.dto.response.InventoryResponse;
import com.omc.product.presentation.dto.response.InventorySnapshotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // 재고 스냅샷 조회 (Internal — Drop Service용)
    // Gateway에서 외부 요청 차단
    @GetMapping("/snapshot")
    public ResponseEntity<InventorySnapshotResponse> getSnapshot(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(inventoryService.getSnapshot(productId));
    }

    // 재고 상세 조회 (ADMIN)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> getInventory(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(inventoryService.getInventory(productId));
    }

    // 재고 수동 수정 (ADMIN)
    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryUpdateRequest request
    ) {
        return ResponseEntity.ok(inventoryService.updateInventory(productId, request));
    }
}