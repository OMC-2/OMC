package com.omc.raffle.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.raffle.application.service.RaffleAppService;
import com.omc.raffle.application.service.RaffleResultService;
import com.omc.raffle.presentation.dto.request.RaffleApplyRequest;
import com.omc.raffle.presentation.dto.request.RaffleEnterRequest;
import com.omc.raffle.presentation.dto.response.PublicRaffleResultResponse;
import com.omc.raffle.presentation.dto.response.RaffleApplyResponse;
import com.omc.raffle.presentation.dto.response.RaffleEntryResponse;
import com.omc.raffle.presentation.dto.response.RaffleResponse;
import com.omc.raffle.presentation.dto.response.RaffleResultResponse;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Raffle", description = "래플 조회 및 응모 API")
@RestController
@RequestMapping("/api/v1/raffles")
@RequiredArgsConstructor
public class RaffleController {

    private final RaffleAppService raffleAppService;
    private final RaffleResultService raffleResultService;

    @Operation(summary = "래플 응모", description = "래플에 응모합니다. 빌링키를 통해 선결제가 진행됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "응모 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "중복 응모"),
    })
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/{raffleId}/entries")
    public ApiResponse<RaffleApplyResponse> enterRaffle(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId,
            @RequestBody @Valid RaffleEnterRequest request) {
        RaffleApplyRequest appRequest = new RaffleApplyRequest(
                userId,
                request.billingKeyId(),
                request.couponId(),
                request.originalAmount(),
                request.discountAmount(),
                request.finalAmount()
        );
        RaffleApplyResponse response = raffleAppService.apply(raffleId, appRequest);
        return ApiResponse.created(response);
    }

    @Operation(summary = "래플 목록 조회", description = "전체 래플 목록을 페이지 단위로 조회합니다.")
    @GetMapping
    public ApiResponse<PageResponse<RaffleResponse>> getRaffles(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(raffleAppService.getRaffles(pageable));
    }

    @Operation(summary = "래플 단건 조회", description = "래플 ID로 래플 상세 정보를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음"),
    })
    @GetMapping("/{raffleId}")
    public ApiResponse<RaffleResponse> getRaffle(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId) {
        return ApiResponse.success(raffleAppService.getRaffle(raffleId));
    }

    @Operation(summary = "내 응모 내역 조회", description = "로그인한 사용자의 래플 응모 내역을 페이지 단위로 조회합니다.")
    @GetMapping("/entries/me")
    public ApiResponse<PageResponse<RaffleEntryResponse>> getMyEntries(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(sort = "enteredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(raffleAppService.getMyEntries(userId, pageable));
    }

    @Operation(summary = "내 당첨 결과 조회", description = "특정 래플에 대한 본인의 당첨/낙첨 결과를 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "결과 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "응모 내역 없음 또는 추첨 미완료"),
    })
    @GetMapping("/{raffleId}/winners/me")
    public ApiResponse<RaffleResultResponse> getMyResult(
            @Parameter(hidden = true) @RequestHeader("X-User-Id") UUID userId,
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId) {
        return ApiResponse.success(raffleResultService.getResult(raffleId, userId));
    }

    @Operation(summary = "공개 당첨자 목록 조회", description = "추첨이 완료된 래플의 당첨자 목록을 공개 조회합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "래플 없음 또는 추첨 미완료"),
    })
    @GetMapping("/{raffleId}/winners")
    public ApiResponse<List<PublicRaffleResultResponse>> getPublicWinners(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId) {
        return ApiResponse.success(raffleResultService.getPublicResults(raffleId));
    }

    @Operation(summary = "실시간 응모자 수 조회", description = "현재까지 해당 래플에 응모한 총 인원 수를 조회합니다.")
    @GetMapping("/{raffleId}/participants-count")
    public ApiResponse<Long> getParticipantsCount(
            @Parameter(description = "래플 ID", required = true) @PathVariable UUID raffleId) {
        return ApiResponse.success(raffleAppService.getParticipantsCount(raffleId));
    }
}
