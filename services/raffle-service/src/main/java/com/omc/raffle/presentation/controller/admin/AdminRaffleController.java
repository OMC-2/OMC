package com.omc.raffle.presentation.controller.admin;

import com.omc.common.response.ApiResponse;
import com.omc.raffle.application.service.RaffleDrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;

import com.omc.common.response.PageResponse;
import com.omc.raffle.presentation.dto.response.RaffleEntryResponse;
import com.omc.raffle.presentation.dto.response.RaffleResponse;
import com.omc.raffle.application.service.AdminRaffleAppService;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleCreateRequest;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleStatusUpdateRequest;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleUpdateRequest;

@RestController
@RequestMapping("/api/v1/admin/raffles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRaffleController {

    private final RaffleDrawService raffleDrawService;
    private final AdminRaffleAppService adminRaffleAppService;

    /**
     * 수동 추첨 실행 API
     * POST /api/v1/admin/raffles/{raffleId}/draw
     */
    @PostMapping("/{raffleId}/draw")
    public ApiResponse<Void> drawRaffle(
            @PathVariable UUID raffleId) {
        
        raffleDrawService.drawRaffle(raffleId);
        return ApiResponse.ok();
    }

    /**
     * 래플 생성 API
     * POST /api/v1/admin/raffles
     */
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<RaffleResponse> createRaffle(
            @RequestBody @Valid AdminRaffleCreateRequest request) {
        RaffleResponse response = adminRaffleAppService.createRaffle(request);
        return ApiResponse.created(response);
    }

    /**
     * 래플 수정 API
     * PUT /api/v1/admin/raffles/{raffleId}
     */
    @PutMapping("/{raffleId}")
    public ApiResponse<Void> updateRaffle(
            @PathVariable UUID raffleId,
            @RequestBody @Valid AdminRaffleUpdateRequest request) {
        adminRaffleAppService.updateRaffle(raffleId, request);
        return ApiResponse.ok();
    }

    /**
     * 래플 취소(삭제) API
     * DELETE /api/v1/admin/raffles/{raffleId}
     */
    @DeleteMapping("/{raffleId}")
    public ApiResponse<Void> deleteRaffle(
            @PathVariable UUID raffleId) {
        adminRaffleAppService.deleteRaffle(raffleId);
        return ApiResponse.ok();
    }

    /**
     * 상태 강제 변경 API
     * POST /api/v1/admin/raffles/{raffleId}/status
     */
    @PostMapping("/{raffleId}/status")
    public ApiResponse<Void> updateRaffleStatus(
            @PathVariable UUID raffleId,
            @RequestBody @Valid AdminRaffleStatusUpdateRequest request) {
        adminRaffleAppService.updateRaffleStatus(raffleId, request);
        return ApiResponse.ok();
    }

    /**
     * 응모자 목록 조회 API
     * GET /api/v1/admin/raffles/{raffleId}/entries
     */
    @GetMapping("/{raffleId}/entries")
    public ApiResponse<PageResponse<RaffleEntryResponse>> getRaffleEntries(
            @PathVariable UUID raffleId,
            @PageableDefault(sort = "enteredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<RaffleEntryResponse> response = adminRaffleAppService.getRaffleEntries(raffleId, pageable);
        return ApiResponse.success(response);
    }
}
