package com.omc.coupon.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.OutboxEventType;
import com.omc.coupon.domain.exception.CouponAlreadyIssuedException;
import com.omc.coupon.domain.exception.CouponErrorCode;
import com.omc.coupon.domain.exception.CouponNotFoundException;
import com.omc.coupon.domain.exception.CouponOutOfStockException;
import com.omc.coupon.domain.repository.CouponRepository;
import com.omc.coupon.domain.repository.OutboxEventRepository;
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
import com.omc.common.security.SecurityUtil;
import com.omc.common.util.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final CouponCacheRepository couponCacheRepository;
    private final CouponMetrics couponMetrics;
    private final CouponStockRecoveryService couponStockRecoveryService;
    private final CouponIssueProducer couponIssueProducer;

    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        Coupon coupon = couponRepository.save(request.toEntity());
        couponRedisRepository.initStock(coupon.getCouponId().toString(), coupon.getTotalQuantity());
        couponCacheRepository.put(coupon);
        return CouponResponse.from(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> getCoupons(Pageable pageable) {
        return couponRepository.findAllByDeletedAtIsNull(pageable).map(CouponResponse::from);
    }

    @Transactional
    public void deleteCoupon(UUID couponId) {
        Coupon coupon = findCoupon(couponId);
        coupon.softDelete(SecurityUtil.getCurrentUserId().orElse(null));
        couponRedisRepository.deleteStock(couponId.toString());
        log.info("[CouponService] 쿠폰 소프트 삭제 완료. couponId={}", couponId);
    }

    @Transactional(readOnly = true)
    public CouponResponse getCoupon(UUID couponId) {
        return CouponResponse.from(findCoupon(couponId));
    }

    public void issueCoupon(UUID couponId, UUID userId) {
        // UUID -> String 변환 캐싱 (요청당 단 1회만 변환하여 GC 압박 극단적 감소)
        String couponIdStr = couponId.toString();
        String userIdStr = userId.toString();

        CouponCacheDto couponDto = findCouponDto(couponId);

        // System.currentTimeMillis() 기반 무객체(No-Object) 시간 검증
        long nowMillis = System.currentTimeMillis();
        if (couponDto.getExpiredAtMillis() < nowMillis) {
            throw new BusinessException(CouponErrorCode.COUPON_EXPIRED);
        }
        if (couponDto.getStartedAtMillis() > nowMillis) {
            throw new BusinessException(CouponErrorCode.COUPON_NOT_STARTED);
        }

        // Scale-out safe: Redis Lua를 단일 원장으로 사용해 모든 인스턴스가 같은 재고/중복 상태를 본다.
        long result = couponMetrics.recordRedisDuration(
                couponIdStr,
                () -> couponRedisRepository.tryIssueWithStockCheck(couponIdStr, userIdStr)
        );
        if (result == -3) {
            couponStockRecoveryService.syncCouponStock(couponId);
            result = couponRedisRepository.tryIssue(couponIdStr, userIdStr);
        }
        if (result == -2) {
            couponMetrics.incrementDuplicate(couponIdStr);
            throw new CouponAlreadyIssuedException();
        }
        if (result == -1) {
            couponMetrics.incrementOutOfStock(couponIdStr);
            throw new CouponOutOfStockException();
        }

        // DB remaining_quantity 차감
        coupon.decreaseRemainingQuantity();

        // DB 중복 체크 (Redis Set과 이중 방어)
        userCouponRepository.findByUserIdAndCoupon_CouponId(userId, couponId).ifPresent(uc -> {
            couponRedisRepository.incrementStock(couponId.toString()); // 재고 롤백
            couponRedisRepository.markIssued(couponId.toString(), userId.toString()); // Redis Set 동기화
            couponMetrics.incrementDuplicate(couponId.toString());
            throw new CouponAlreadyIssuedException();
        });

        // UserCoupon 저장 (saveAndFlush로 즉시 INSERT → UNIQUE 위반 시 여기서 예외 발생, 재고 롤백)
        UserCoupon userCoupon;
        try {
            userCoupon = userCouponRepository.saveAndFlush(
                    UserCoupon.create(userId, coupon, coupon.getExpiredAt())
            );
        } catch (DataIntegrityViolationException e) {
            couponRedisRepository.incrementStock(couponId.toString());
            coupon.increaseRemainingQuantity(); // DB 롤백
            couponMetrics.incrementDuplicate(couponId.toString());
            throw new CouponAlreadyIssuedException();
        }
        couponIssueProducer.publish(couponId, userId); // 비동기 발행 — 실패 시 whenComplete에서 Redis 롤백

        couponMetrics.incrementIssueSuccess(couponIdStr);
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
        return couponRepository.findByCouponIdAndDeletedAtIsNull(couponId).orElseThrow(CouponNotFoundException::new);
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
