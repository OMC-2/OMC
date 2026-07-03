package com.omc.coupon.domain.repository;

import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.UserCouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, UUID> {

    Optional<UserCoupon> findByOrderIdAndStatus(UUID orderId, UserCouponStatus status);

    Optional<UserCoupon> findByUserIdAndCoupon_CouponId(UUID userId, UUID couponId);

    Page<UserCoupon> findByUserId(UUID userId, Pageable pageable);

    List<UserCoupon> findByStatusInAndExpiredAtBefore(List<UserCouponStatus> statuses, LocalDateTime now);

    long countByCoupon_CouponId(UUID couponId);

    @Modifying
    @Query("DELETE FROM UserCoupon uc WHERE uc.coupon.couponId = :couponId")
    void deleteByCoupon_CouponId(@Param("couponId") UUID couponId);
}
