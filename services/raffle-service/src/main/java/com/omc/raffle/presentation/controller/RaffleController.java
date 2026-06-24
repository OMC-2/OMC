package com.omc.raffle.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.raffle.presentation.dto.request.RaffleApplyRequest;
import com.omc.raffle.presentation.dto.response.RaffleApplyResponse;
import com.omc.raffle.application.service.RaffleAppService;
import com.omc.raffle.application.service.RaffleResultService;
import com.omc.raffle.presentation.dto.request.RaffleEnterRequest;
import com.omc.raffle.presentation.dto.response.RaffleResultResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import com.omc.common.response.PageResponse;
import com.omc.raffle.presentation.dto.response.RaffleResponse;
import com.omc.raffle.presentation.dto.response.RaffleEntryResponse;

import java.util.UUID;

/**
 * 외부 클라이언트(웹/앱)의 래플 도메인 관련 요청을 처리하는 Presentation 계층 Controller.
 * 래플 응모(결제 연동 포함) 등의 진입점 역할을 합니다.
 */
@RestController
@RequestMapping("/api/v1/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleAppService raffleAppService;
    private final RaffleResultService raffleResultService;

    /**
     * 래플 응모 (선결제) API
     * POST /api/v1/raffles/{raffleId}/entries
     */
    @PostMapping("/{raffleId}/entries")
    public ResponseEntity<ApiResponse<RaffleApplyResponse>> enterRaffle(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID raffleId,
            @RequestBody @Valid RaffleEnterRequest request) {
        
        // Controller DTO -> Application DTO 변환 및 userId 주입
        RaffleApplyRequest appRequest = new RaffleApplyRequest(
                userId, 
                request.billingKeyId(),
                request.couponId(),
                request.originalAmount(),
                request.discountAmount(),
                request.finalAmount()
        );
        
        RaffleApplyResponse response = raffleAppService.apply(raffleId, appRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    /**
     * 래플 목록 조회 API
     * GET /api/v1/raffles
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RaffleResponse>>> getRaffles(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<RaffleResponse> response = raffleAppService.getRaffles(pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 래플 상세 단건 조회 API
     * GET /api/v1/raffles/{raffleId}
     */
    @GetMapping("/{raffleId}")
    public ResponseEntity<ApiResponse<RaffleResponse>> getRaffle(
            @PathVariable UUID raffleId) {
        RaffleResponse response = raffleAppService.getRaffle(raffleId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 내 응모 내역 조회 API
     * GET /api/v1/raffles/entries/me
     */
    @GetMapping("/entries/me")
    public ResponseEntity<ApiResponse<PageResponse<RaffleEntryResponse>>> getMyEntries(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(sort = "enteredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<RaffleEntryResponse> response = raffleAppService.getMyEntries(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 내 당첨 결과 확인 API
     * GET /api/v1/raffles/{raffleId}/winners/me
     */
    @GetMapping("/{raffleId}/winners/me")
    public ResponseEntity<ApiResponse<RaffleResultResponse>> getRaffleResult(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID raffleId) {

        RaffleResultResponse response = 
                raffleResultService.getResult(raffleId, userId);
                
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}


