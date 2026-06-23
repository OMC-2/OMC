package com.omc.coupon.application.scheduler;

import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpiryScheduler {

    private final UserCouponRepository userCouponRepository;

    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    @Transactional
    public void expireOverdueCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> expired = userCouponRepository.findByStatusInAndExpiredAtBefore(
                List.of(UserCouponStatus.AVAILABLE, UserCouponStatus.RESERVED), now
        );

        expired.forEach(UserCoupon::expire);
        log.info("[CouponExpiryScheduler] 만료 처리 완료. count={}", expired.size());
    }
}
