package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponStockRecoveryService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRedisRepository couponRedisRepository;

    /**
     * 서비스 시작 시 전체 쿠폰 Redis 재고를 DB 기준으로 재세팅.
     * DB: 커넥션 준비 완료 후 발생하는 ApplicationReadyEvent 사용.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void syncAllCouponStock() {
        List<Coupon> coupons = couponRepository.findAll();
        for (Coupon coupon : coupons) {
            long issued = userCouponRepository.countByCoupon_CouponId(coupon.getCouponId());
            long remaining = Math.max(0L, coupon.getTotalQuantity() - issued);
            couponRedisRepository.initStock(coupon.getCouponId().toString(), remaining);
            log.info("[CouponStockRecovery] 재고 동기화. couponId={}, remaining={}", coupon.getCouponId(), remaining);
        }
    }

    /**
     * Redis key가 없을 때 단일 쿠폰 재고 복구.
     * CouponService.issueCoupon에서 DECR 전 호출.
     */
    @Transactional(readOnly = true)
    public void syncCouponStock(UUID couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰 없음: " + couponId));
        long issued = userCouponRepository.countByCoupon_CouponId(couponId);
        long remaining = Math.max(0L, coupon.getTotalQuantity() - issued);
        couponRedisRepository.initStock(couponId.toString(), remaining);
        log.info("[CouponStockRecovery] 단일 쿠폰 재고 복구. couponId={}, remaining={}", couponId, remaining);
    }
}
