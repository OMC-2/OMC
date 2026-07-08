package com.omc.coupon.domain.repository;

import com.omc.coupon.domain.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByExpiredAtAfterAndRemainingQuantityGreaterThan(LocalDateTime now, int quantity);

    List<Coupon> findByExpiredAtAfterAndRemainingQuantityGreaterThanOrderByCreatedAtDesc(LocalDateTime now, int quantity, Pageable pageable);

    // soft delete 필터 — deletedAt IS NULL인 것만 조회
    Page<Coupon> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Coupon> findByCouponIdAndDeletedAtIsNull(UUID couponId);
}
