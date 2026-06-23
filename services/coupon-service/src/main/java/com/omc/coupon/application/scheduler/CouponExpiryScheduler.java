package com.omc.coupon.application.scheduler;

import com.omc.coupon.application.service.CouponExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpiryScheduler {

    private final CouponExpiryService couponExpiryService;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void expireOverdueCoupons() {
        couponExpiryService.expireOverdueCoupons();
    }
}
