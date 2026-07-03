package com.omc.coupon.unit.service;

import com.omc.coupon.application.service.CouponStockRecoveryService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponStockRecoveryServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private UserCouponRepository userCouponRepository;
    @Mock private CouponRedisRepository couponRedisRepository;

    @InjectMocks private CouponStockRecoveryService couponStockRecoveryService;

    // =========================================================================
    // [테스트 1] syncCouponStock: DB COUNT로 Redis 재고 세팅
    // plan 테스트 1: key 없을 때 DB COUNT로 복구
    // 구현 전 RED: CouponStockRecoveryService 클래스 없음
    // 구현 후 GREEN: Redis에 (totalQuantity - issuedCount) 세팅
    // =========================================================================

    @Test
    void syncCouponStock_setsCorrectRemainingStock() {
        // given
        UUID couponId = UUID.randomUUID();
        Coupon couponMock = mock(Coupon.class);
        given(couponRepository.findById(couponId)).willReturn(Optional.of(couponMock));
        given(couponMock.getTotalQuantity()).willReturn(100);
        given(userCouponRepository.countByCoupon_CouponId(couponId)).willReturn(30L);

        // when
        couponStockRecoveryService.syncCouponStock(couponId);

        // then: 100 - 30 = 70
        verify(couponRedisRepository).initStock(couponId.toString(), 70L);
    }

    // =========================================================================
    // [테스트 2] syncAllCouponStock: ApplicationReadyEvent 시 전체 쿠폰 Redis 세팅
    // plan 테스트 3: 쿠폰 2개 각각 올바른 값으로 Redis 세팅
    // 구현 전 RED: CouponStockRecoveryService 클래스 없음
    // 구현 후 GREEN: 각 쿠폰 Redis stock = totalQty - issuedCount
    // =========================================================================

    @Test
    void syncAllCouponStock_setsCorrectStockForAllCoupons() {
        // given
        UUID couponIdA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID couponIdB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        Coupon couponA = mock(Coupon.class);
        Coupon couponB = mock(Coupon.class);
        given(couponA.getCouponId()).willReturn(couponIdA);
        given(couponA.getTotalQuantity()).willReturn(100);
        given(couponB.getCouponId()).willReturn(couponIdB);
        given(couponB.getTotalQuantity()).willReturn(50);

        given(couponRepository.findAll()).willReturn(List.of(couponA, couponB));
        given(userCouponRepository.countByCoupon_CouponId(couponIdA)).willReturn(30L);
        given(userCouponRepository.countByCoupon_CouponId(couponIdB)).willReturn(20L);

        // when
        couponStockRecoveryService.syncAllCouponStock();

        // then
        verify(couponRedisRepository).initStock(couponIdA.toString(), 70L); // 100 - 30
        verify(couponRedisRepository).initStock(couponIdB.toString(), 30L); // 50 - 20
    }
}
