package com.omc.drop.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.drop.application.service.DropQueryService;
import com.omc.drop.presentation.dto.response.ActiveDropResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/drops")
@RequiredArgsConstructor
public class DropInternalController {

    private final DropQueryService dropQueryService;

    @GetMapping("/products/{productId}/active")
    public ResponseEntity<ApiResponse<ActiveDropResponse>> hasActiveDrop(@PathVariable UUID productId) {
        ActiveDropResponse response = dropQueryService.hasActiveDrop(productId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
