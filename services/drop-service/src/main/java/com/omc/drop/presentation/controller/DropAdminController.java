package com.omc.drop.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.drop.application.service.DropAdminService;
import com.omc.drop.presentation.dto.request.DropCreateRequest;
import com.omc.drop.presentation.dto.request.DropUpdateRequest;
import com.omc.drop.presentation.dto.response.DropAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Drop Admin", description = "드롭 관리 (어드민 전용)")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/drops")
@RequiredArgsConstructor
public class DropAdminController {

    private final DropAdminService dropAdminService;

    @Operation(summary = "드롭 생성", description = "새로운 드롭을 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<DropAdminResponse>> create(@Valid @RequestBody DropCreateRequest request) {
        DropAdminResponse response = dropAdminService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "드롭 수정", description = "SCHEDULED 상태의 드롭 정보를 수정합니다.")
    @PutMapping("/{dropId}")
    public ResponseEntity<ApiResponse<DropAdminResponse>> update(
            @PathVariable UUID dropId,
            @Valid @RequestBody DropUpdateRequest request
    ) {
        DropAdminResponse response = dropAdminService.update(dropId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "드롭 삭제", description = "SCHEDULED 상태의 드롭을 소프트딜리트합니다.")
    @DeleteMapping("/{dropId}")
    public ResponseEntity<Void> delete(@PathVariable UUID dropId) {
        dropAdminService.delete(dropId);
        return ResponseEntity.noContent().build();
    }
}
