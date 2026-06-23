package com.omc.coupon.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.coupon.application.service.CouponReserveService;
import com.omc.coupon.presentation.dto.request.CouponReserveRequest;
import com.omc.coupon.presentation.dto.response.CouponReserveResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coupons")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponReserveService couponReserveService;

    // payment-service가 FeignClient로 호출 — 쿠폰 선점 (AVAILABLE → RESERVED)
    @PostMapping("/reserve")
    public ApiResponse<CouponReserveResponse> reserve(@Valid @RequestBody CouponReserveRequest request) {
        return ApiResponse.success(couponReserveService.reserve(request));
    }

    // payment-service가 FeignClient로 호출 — 쿠폰 유효성 조회
    @GetMapping("/{userCouponId}")
    public ApiResponse<UserCouponResponse> getUserCoupon(@PathVariable UUID userCouponId) {
        return ApiResponse.success(
                UserCouponResponse.from(couponReserveService.getUserCoupon(userCouponId))
        );
    }
}
