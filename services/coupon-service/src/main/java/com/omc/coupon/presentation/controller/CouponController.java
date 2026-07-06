package com.omc.coupon.presentation.controller;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.coupon.application.service.CouponService;
import com.omc.coupon.application.service.CouponTicketService;
import com.omc.coupon.presentation.dto.request.CouponCreateRequest;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import com.omc.coupon.presentation.dto.response.CouponTicketResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final CouponTicketService couponTicketService;

    // ADMIN: 쿠폰 생성
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CouponResponse> createCoupon(@Valid @RequestBody CouponCreateRequest request) {
        return ApiResponse.success(couponService.createCoupon(request));
    }

    // ADMIN: 쿠폰 목록 조회
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<CouponResponse>> getCoupons(Pageable pageable) {
        Page<CouponResponse> page = couponService.getCoupons(pageable);
        return ApiResponse.success(new PageResponse<>(page));
    }

    // ADMIN: 쿠폰 상세 조회
    @GetMapping("/{couponId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CouponResponse> getCoupon(@PathVariable UUID couponId) {
        return ApiResponse.success(couponService.getCoupon(couponId));
    }

    // USER: 이벤트용 AES 사전 인증 티켓 발급 (스파이크 전 미리 발급, Gateway ES256 검증 대체)
    @PostMapping("/{couponId}/ticket")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<CouponTicketResponse> issueTicket(@PathVariable UUID couponId) {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        String ticket = couponTicketService.issueTicket(userId);
        return ApiResponse.success(new CouponTicketResponse(ticket));
    }

    // USER: 선착순 쿠폰 발급
    @PostMapping("/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<Void> issueCoupon(@PathVariable UUID couponId) {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        couponService.issueCoupon(couponId, userId);
        return ApiResponse.success("쿠폰이 발급되었습니다.", null);
    }

    // USER: 내 쿠폰 목록 조회
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<PageResponse<UserCouponResponse>> getMyCoupons(Pageable pageable) {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        Page<UserCouponResponse> page = couponService.getMyCoupons(userId, pageable);
        return ApiResponse.success(new PageResponse<>(page));
    }

    // USER: 내 쿠폰 상세 조회
    @GetMapping("/me/{userCouponId}")
    @PreAuthorize("hasRole('USER')")
    public ApiResponse<UserCouponResponse> getMyCoupon(@PathVariable UUID userCouponId) {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return ApiResponse.success(couponService.getMyCoupon(userId, userCouponId));
    }
}
