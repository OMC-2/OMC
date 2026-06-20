package com.omc.product.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.service.InventoryService;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import com.omc.product.presentation.dto.response.InventoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Inventory Admin", description = "재고 관리 (어드민 전용)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/products/{productId}/inventories")
@RequiredArgsConstructor
public class InventoryAdminController {

    private final InventoryService inventoryService;

    @Operation(summary = "재고 상세 조회", description = "상품의 재고 상세 정보를 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getInventory(productId)));
    }

    @Operation(summary = "재고 수동 수정", description = "재고 수량을 수동으로 수정합니다.")
    @PatchMapping
    public ResponseEntity<ApiResponse<InventoryResponse>> updateInventory(
            @PathVariable UUID productId,
            @Valid @RequestBody InventoryUpdateRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(inventoryService.updateInventory(productId, request))
        );
    }
}
