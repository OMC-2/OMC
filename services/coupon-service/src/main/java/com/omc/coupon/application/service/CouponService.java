package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.exception.CouponAlreadyIssuedException;
import com.omc.coupon.domain.exception.CouponErrorCode;
import com.omc.coupon.domain.exception.CouponNotFoundException;
import com.omc.coupon.domain.exception.CouponOutOfStockException;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.kafka.CouponIssueProducer;
import com.omc.coupon.infrastructure.metrics.CouponMetrics;
import com.omc.coupon.infrastructure.redis.CouponCacheDto;
import com.omc.coupon.infrastructure.redis.CouponCacheRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import com.omc.coupon.infrastructure.store.CouponLocalStore;
import com.omc.coupon.presentation.dto.request.CouponCreateRequest;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import com.omc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final CouponMetrics couponMetrics;
    private final CouponStockRecoveryService couponStockRecoveryService;
    private final CouponIssueProducer couponIssueProducer;
    private final CouponLocalStore couponLocalStore;

    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        Coupon coupon = couponRepository.save(request.toEntity());
        couponRedisRepository.initStock(coupon.getCouponId().toString(), coupon.getTotalQuantity());
        couponCacheRepository.put(coupon);
        couponLocalStore.initCoupon(coupon.getCouponId().toString(), coupon.getTotalQuantity());
        return CouponResponse.from(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> getCoupons(Pageable pageable) {
        return couponRepository.findAll(pageable).map(CouponResponse::from);
    }

    @Transactional(readOnly = true)
    public CouponResponse getCoupon(UUID couponId) {
        return CouponResponse.from(findCoupon(couponId));
    }

    public void issueCoupon(UUID couponId, UUID userId) {
        CouponCacheDto couponDto = findCouponDto(couponId);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (couponDto.getExpiredAt().isBefore(now)) {
            throw new BusinessException(CouponErrorCode.COUPON_EXPIRED);
        }
        if (couponDto.getStartedAt().isAfter(now)) {
            throw new BusinessException(CouponErrorCode.COUPON_NOT_STARTED);
        }

        // 인메모리 CAS 기반 재고 차감 + 중복 확인 (Redis 왕복 없음)
        // -3: LocalStore 미초기화 → Redis Lua fallback
        long result = couponLocalStore.tryIssue(couponId.toString(), userId.toString());
        if (result == -3) {
            result = couponMetrics.recordRedisDuration(
                    couponId.toString(),
                    () -> couponRedisRepository.tryIssueWithStockCheck(couponId.toString(), userId.toString())
            );
            if (result == -3) {
                couponStockRecoveryService.syncCouponStock(couponId);
                result = couponRedisRepository.tryIssue(couponId.toString(), userId.toString());
            }
        }
        if (result == -2) {
            couponMetrics.incrementDuplicate(couponId.toString());
            throw new CouponAlreadyIssuedException();
        }
        if (result == -1) {
            couponMetrics.incrementOutOfStock(couponId.toString());
            throw new CouponOutOfStockException();
        }

        couponIssueProducer.publish(couponId, userId); // 비동기 발행 — 실패 시 whenComplete에서 Redis 롤백

        couponMetrics.incrementIssueSuccess(couponId.toString());
    }

    @Transactional(readOnly = true)
    public Page<UserCouponResponse> getMyCoupons(UUID userId, Pageable pageable) {
        return userCouponRepository.findByUserId(userId, pageable).map(UserCouponResponse::from);
    }

    @Transactional(readOnly = true)
    public UserCouponResponse getMyCoupon(UUID userId, UUID userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .filter(uc -> uc.getUserId().equals(userId))
                .orElseThrow(com.omc.coupon.domain.exception.UserCouponNotFoundException::new);
        return UserCouponResponse.from(userCoupon);
    }

    private Coupon findCoupon(UUID couponId) {
        return couponRepository.findById(couponId).orElseThrow(CouponNotFoundException::new);
    }

    private CouponCacheDto findCouponDto(UUID couponId) {
        return couponCacheRepository.get(couponId)
                .orElseGet(() -> {
                    Coupon coupon = couponRepository.findById(couponId)
                            .orElseThrow(CouponNotFoundException::new);
                    couponCacheRepository.put(coupon);
                    return CouponCacheDto.from(coupon);
                });
    }

}
