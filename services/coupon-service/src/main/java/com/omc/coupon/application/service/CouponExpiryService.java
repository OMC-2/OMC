package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponExpiryService {

    private final UserCouponRepository userCouponRepository;

    @Transactional
    public void expireOverdueCoupons() {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> expired = userCouponRepository.findByStatusInAndExpiredAtBefore(
                List.of(UserCouponStatus.AVAILABLE, UserCouponStatus.RESERVED), now
        );
        expired.forEach(UserCoupon::expire);
    }
}
