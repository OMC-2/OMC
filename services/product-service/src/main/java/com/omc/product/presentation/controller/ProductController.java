package com.omc.product.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.product.application.service.ProductService;
import com.omc.product.presentation.dto.request.ProductCreateRequest;
import com.omc.product.presentation.dto.request.ProductUpdateRequest;
import com.omc.product.presentation.dto.response.ProductResponse;
import com.omc.product.presentation.dto.response.ProductSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSummaryResponse>>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Page<ProductSummaryResponse> response = productService.getProducts(category, brand, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(new PageResponse<>(response)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable UUID productId) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductResponse response = productService.updateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID productId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        productService.deleteProduct(productId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}