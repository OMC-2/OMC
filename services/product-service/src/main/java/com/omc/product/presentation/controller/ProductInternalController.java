package com.omc.product.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.service.ProductService;
import com.omc.product.presentation.dto.response.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Product Internal", description = "상품 내부 API (서비스 간 통신 전용)")
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    @Operation(summary = "상품 상세 조회", description = "Order Service 등 내부 서비스에서 상품 가격 조회 시 사용합니다.")
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProduct(productId)));
    }
}
