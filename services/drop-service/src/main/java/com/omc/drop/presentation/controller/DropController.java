package com.omc.drop.presentation.controller;

import com.omc.common.exception.UnauthorizedException;
import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.drop.application.service.DropQueryService;
import com.omc.drop.application.service.PurchaseService;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.presentation.dto.response.DropResponse;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Drop", description = "드롭 조회 및 구매")
@RestController
@RequestMapping("/api/v1/drops")
@RequiredArgsConstructor
public class DropController {

    private final DropQueryService dropQueryService;
    private final PurchaseService purchaseService;

    @Operation(summary = "드롭 목록 조회", description = "status 필터로 드롭 목록을 페이지 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DropResponse>>> findAll(
            @RequestParam(required = false) DropStatus status,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<DropResponse> response = dropQueryService.findAll(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "드롭 단건 조회", description = "dropId로 드롭 상세를 조회합니다.")
    @GetMapping("/{dropId}")
    public ResponseEntity<ApiResponse<DropResponse>> findById(@PathVariable UUID dropId) {
        DropResponse response = dropQueryService.findById(dropId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "드롭 구매 선점", description = "OPEN 상태의 드롭에 선착순으로 진입합니다. 성공 시 orderId와 대기 순번을 반환합니다.")
    @PostMapping("/{dropId}/purchase")
    public ResponseEntity<ApiResponse<PurchaseResponse>> purchase(@PathVariable UUID dropId) {
        UUID userId = SecurityUtil.getCurrentUserId().orElseThrow(UnauthorizedException::new);
        PurchaseResponse response = purchaseService.purchase(dropId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(response));
    }
}
