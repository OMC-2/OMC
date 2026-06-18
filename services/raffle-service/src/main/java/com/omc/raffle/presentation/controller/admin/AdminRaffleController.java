package com.omc.raffle.presentation.controller.admin;

import com.omc.common.response.ApiResponse;
import com.omc.raffle.application.service.RaffleDrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/raffles")
@RequiredArgsConstructor
public class AdminRaffleController {

    private final RaffleDrawService raffleDrawService;

    /**
     * 수동 추첨 실행 API
     * POST /api/v1/admin/raffles/{raffleId}/draw
     */
    @PostMapping("/{raffleId}/draw")
    public ResponseEntity<ApiResponse<Void>> drawRaffle(
            @PathVariable UUID raffleId) {
        
        raffleDrawService.drawRaffle(raffleId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
