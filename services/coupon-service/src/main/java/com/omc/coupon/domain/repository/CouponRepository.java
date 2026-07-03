package com.omc.coupon.domain.repository;

import com.omc.coupon.domain.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    List<Coupon> findByExpiredAtAfterAndRemainingQuantityGreaterThan(LocalDateTime now, int quantity);
}
