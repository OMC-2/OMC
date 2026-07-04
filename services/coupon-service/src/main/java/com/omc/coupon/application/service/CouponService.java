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
import com.omc.coupon.presentation.dto.request.CouponCreateRequest;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import com.omc.common.exception.BusinessException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
    private final Tracer tracer;

    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        Coupon coupon = couponRepository.save(request.toEntity());
        couponRedisRepository.initStock(coupon.getCouponId().toString(), coupon.getTotalQuantity());
        couponCacheRepository.put(coupon);
        log.info("[CouponService] 쿠폰 생성 완료. couponId={}", coupon.getCouponId());
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

        // [validate] 날짜 유효성 검사 — 재고는 Redis DECR이 제어
        Span validateSpan = tracer.nextSpan().name("coupon.issue.validate").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(validateSpan)) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            if (couponDto.getExpiredAt().isBefore(now)) {
                throw new BusinessException(CouponErrorCode.COUPON_EXPIRED);
            }
            if (couponDto.getStartedAt().isAfter(now)) {
                throw new BusinessException(CouponErrorCode.COUPON_NOT_STARTED);
            }
        } finally {
            validateSpan.end();
        }

        // Redis 이중 방어: 이미 발급 여부 확인
        if (couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())) {
            couponMetrics.incrementDuplicate(couponId.toString());
            throw new CouponAlreadyIssuedException();
        }

        // Redis key 없으면 DB COUNT로 즉시 복구 (케이스 1: Redis만 죽은 경우)
        if (!couponRedisRepository.hasStock(couponId.toString())) {
            couponStockRecoveryService.syncCouponStock(couponId);
        }

        // Redis 원자적 재고 차감
        long remaining = couponMetrics.recordRedisDuration(
                couponId.toString(),
                () -> couponRedisRepository.decrementStock(couponId.toString())
        );
        if (remaining < 0) {
            couponRedisRepository.incrementStock(couponId.toString()); // 롤백
            couponMetrics.incrementOutOfStock(couponId.toString());
            throw new CouponOutOfStockException();
        }

        couponRedisRepository.markIssued(couponId.toString(), userId.toString());
        couponIssueProducer.publish(couponId, userId); // 비동기 발행 — 실패 시 whenComplete에서 Redis 롤백

        couponMetrics.incrementIssueSuccess(couponId.toString());
        log.info("[CouponService] 쿠폰 발급 요청 완료. couponId={}, userId={}", couponId, userId);
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
