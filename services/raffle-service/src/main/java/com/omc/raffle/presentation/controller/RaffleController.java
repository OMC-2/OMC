package com.omc.raffle.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.raffle.application.dto.request.RaffleApplyRequest;
import com.omc.raffle.application.dto.response.RaffleApplyResponse;
import com.omc.raffle.application.service.RaffleAppService;
import com.omc.raffle.presentation.dto.request.RaffleEnterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleAppService raffleAppService;

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
        RaffleApplyRequest appRequest = new RaffleApplyRequest(userId, request.billingKeyId());
        
        RaffleApplyResponse response = raffleAppService.apply(raffleId, appRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
