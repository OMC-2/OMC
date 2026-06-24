package com.omc.coupon.unit.entity;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CouponEntityUuidV7Test {

    // =========================================================================
    // [1] Coupon.create() → couponId version 7
    // =========================================================================

    @Test
    void create_couponId_isVersion7() {
        Coupon coupon = coupon();

        assertThat(coupon.getCouponId().version()).isEqualTo(7);
    }

    // =========================================================================
    // [2] UserCoupon.create() → userCouponId version 7
    // =========================================================================

    @Test
    void create_userCouponId_isVersion7() {
        UserCoupon userCoupon = UserCoupon.create(UUID.randomUUID(), coupon(), LocalDateTime.now().plusDays(7));

        assertThat(userCoupon.getUserCouponId().version()).isEqualTo(7);
    }

    // =========================================================================
    // [3] 연속 생성한 Coupon ID가 시간순으로 정렬되는지 확인
    //     UuidV7Generator를 실제로 호출하는지 종단 검증
    // =========================================================================

    @Test
    void create_multipleCoupons_idsAreSortedByCreationOrder() throws InterruptedException {
        Coupon first = coupon();
        Thread.sleep(1);
        Coupon second = coupon();

        assertThat(first.getCouponId().toString()).isLessThan(second.getCouponId().toString());
    }

    // =========================================================================
    // [4] 연속 생성한 UserCoupon ID가 시간순으로 정렬되는지 확인
    // =========================================================================

    @Test
    void create_multipleUserCoupons_idsAreSortedByCreationOrder() throws InterruptedException {
        Coupon coupon = coupon();
        UserCoupon first = UserCoupon.create(UUID.randomUUID(), coupon, LocalDateTime.now().plusDays(7));
        Thread.sleep(1);
        UserCoupon second = UserCoupon.create(UUID.randomUUID(), coupon, LocalDateTime.now().plusDays(7));

        assertThat(first.getUserCouponId().toString()).isLessThan(second.getUserCouponId().toString());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Coupon coupon() {
        return Coupon.create(
                "테스트 쿠폰", DiscountType.AMOUNT, new BigDecimal("1000"),
                null, 100, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(7));
    }
}
