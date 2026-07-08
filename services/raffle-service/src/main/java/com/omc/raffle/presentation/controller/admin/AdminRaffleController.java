package com.omc.raffle.presentation.controller.admin;

import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.raffle.application.service.AdminRaffleAppService;
import com.omc.raffle.application.service.RaffleDrawService;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleCreateRequest;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleStatusUpdateRequest;
import com.omc.raffle.presentation.dto.request.admin.AdminRaffleUpdateRequest;
import com.omc.raffle.presentation.dto.response.RaffleEntryResponse;
import com.omc.raffle.presentation.dto.response.RaffleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin - Raffle", description = "래플 관리 API (ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/admin/raffles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRaffleController {

    private final RaffleDrawService raffleDrawService;
    private final AdminRaffleAppService adminRaffleAppService;

    @Operation(summary = "수동 추첨 실행", description = "종료된 래플에 대해 수동으로 추첨을 실행합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "추첨 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 추첨된 래플"),
    })
    @PostMapping("/{raffleId}/draw")
    public ApiResponse<Void> drawRaffle(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId) {
        raffleDrawService.drawRaffle(raffleId);
        return ApiResponse.ok();
    }

    @Operation(summary = "래플 생성", description = "새 래플을 생성합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<RaffleResponse> createRaffle(
            @RequestBody @Valid AdminRaffleCreateRequest request) {
        RaffleResponse response = adminRaffleAppService.createRaffle(request);
        return ApiResponse.created(response);
    }

    @Operation(summary = "래플 수정", description = "래플 이름과 당첨 인원을 수정합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음"),
    })
    @PutMapping("/{raffleId}")
    public ApiResponse<Void> updateRaffle(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId,
            @RequestBody @Valid AdminRaffleUpdateRequest request) {
        adminRaffleAppService.updateRaffle(raffleId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "래플 삭제", description = "래플을 소프트 삭제합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음"),
    })
    @DeleteMapping("/{raffleId}")
    public ApiResponse<Void> deleteRaffle(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId) {
        adminRaffleAppService.deleteRaffle(raffleId);
        return ApiResponse.ok();
    }

    @Operation(summary = "래플 상태 강제 변경", description = "래플 상태를 SCHEDULED / OPEN / CLOSED 중 하나로 강제 변경합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상태 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않은 상태 값"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음"),
    })
    @PostMapping("/{raffleId}/status")
    public ApiResponse<Void> updateRaffleStatus(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId,
            @RequestBody @Valid AdminRaffleStatusUpdateRequest request) {
        adminRaffleAppService.updateRaffleStatus(raffleId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "응모자 목록 조회", description = "특정 래플의 전체 응모자 목록을 페이지 단위로 조회합니다.")
    @GetMapping("/{raffleId}/entries")
    public ApiResponse<PageResponse<RaffleEntryResponse>> getRaffleEntries(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId,
            @PageableDefault(sort = "enteredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<RaffleEntryResponse> response = adminRaffleAppService.getRaffleEntries(raffleId, pageable);
        return ApiResponse.success(response);
    }

    @Operation(summary = "패널티 부여", description = "특정 사용자에게 30일 래플 패널티를 부여합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "패널티 부여 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음"),
    })
    @PostMapping("/{raffleId}/entries/{userId}/penalty")
    public ApiResponse<Void> penalizeUser(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId,
            @Parameter(description = "패널티 대상 유저 ID", required = true) @PathVariable UUID userId) {
        adminRaffleAppService.penalizeUser(raffleId, userId);
        return ApiResponse.ok();
    }
}
