package com.omc.coupon.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.coupon.application.service.CouponReserveService;
import com.omc.coupon.presentation.dto.request.CouponReserveRequest;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/coupons")
@RequiredArgsConstructor
public class CouponInternalController {

    private final CouponReserveService couponReserveService;

    @PostMapping("/reserve")
    public ApiResponse<UserCouponResponse> reserve(@Valid @RequestBody CouponReserveRequest request) {
        return ApiResponse.success(couponReserveService.reserve(request));
    }
}
