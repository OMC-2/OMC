package com.omc.payment.infrastructure.client;

import com.omc.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "coupon-service")
public interface CouponServiceClient {

    // 결제 직전에 쿠폰 상태와 할인 정보를 다시 조회
    @GetMapping("/internal/v1/coupons/{userCouponId}")
    ApiResponse<CouponUserCouponResponse> getUserCoupon(@PathVariable UUID userCouponId);
}
