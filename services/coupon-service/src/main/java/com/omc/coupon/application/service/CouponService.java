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
import com.omc.coupon.infrastructure.metrics.CouponMetrics;
import com.omc.coupon.infrastructure.redis.CouponCacheDto;
import com.omc.coupon.infrastructure.redis.CouponCacheRepository;
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
import com.omc.coupon.presentation.dto.request.CouponCreateRequest;
import com.omc.coupon.presentation.dto.response.CouponResponse;
import com.omc.coupon.presentation.dto.response.UserCouponResponse;
import com.omc.common.exception.BusinessException;
import com.omc.common.util.UuidV7Generator;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final ObjectMapper objectMapper;
    private final CouponMetrics couponMetrics;
    private final CouponStockRecoveryService couponStockRecoveryService;
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

    @Transactional
    public UserCouponResponse issueCoupon(UUID couponId, UUID userId) {
        registerTxCommitSpan();

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

        // [pre-db-check] decrementStock 직후 ~ DB 쿼리 직전 갭 구간
        Span preDbCheckSpan = tracer.nextSpan().name("coupon.issue.pre-db-check").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(preDbCheckSpan)) {
            userCouponRepository.findByUserIdAndCoupon_CouponId(userId, couponId).ifPresent(uc -> {
                preDbCheckSpan.end();
                couponRedisRepository.incrementStock(couponId.toString());
                couponRedisRepository.markIssued(couponId.toString(), userId.toString());
                couponMetrics.incrementDuplicate(couponId.toString());
                throw new CouponAlreadyIssuedException();
            });
        } finally {
            preDbCheckSpan.end();
        }

        // [entity-create] UserCoupon 객체 생성 — DB 중복 체크 이후 갭 구간
        // couponRef: JPA 프록시(DB 조회 없음), expiredAt은 캐시에서 가져옴
        UserCoupon newUserCoupon;
        Span entitySpan = tracer.nextSpan().name("coupon.issue.entity-create").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(entitySpan)) {
            Coupon couponRef = couponRepository.getReferenceById(couponId);
            newUserCoupon = UserCoupon.create(userId, couponRef, couponDto.getExpiredAt());
        } finally {
            entitySpan.end();
        }

        // UserCoupon 저장 (saveAndFlush로 즉시 INSERT → UNIQUE 위반 시 여기서 예외 발생, 재고 롤백)
        UserCoupon userCoupon;
        try {
            userCoupon = userCouponRepository.saveAndFlush(newUserCoupon);
        } catch (DataIntegrityViolationException e) {
            couponRedisRepository.incrementStock(couponId.toString());
            couponMetrics.incrementDuplicate(couponId.toString());
            throw new CouponAlreadyIssuedException();
        }

        // [outbox-build] UUID 생성 + JSON 직렬화 — saveAndFlush 이후 갭 구간
        UUID outboxEventId;
        String payload;
        Span outboxBuildSpan = tracer.nextSpan().name("coupon.issue.outbox-build").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(outboxBuildSpan)) {
            outboxEventId = UuidV7Generator.generate();
            payload = toJson(Map.of(
                    "eventId", outboxEventId.toString(),
                    "couponId", couponId.toString(),
                    "userId", userId.toString()
            ));
        } finally {
            outboxBuildSpan.end();
        }

        outboxEventRepository.save(OutboxEvent.create(
                outboxEventId, "UserCoupon", userCoupon.getUserCouponId(), OutboxEventType.COUPON_ISSUED, payload
        ));

        couponRedisRepository.markIssued(couponId.toString(), userId.toString());

        // [response-build] tx-commit 이후 ~ connection 종료 사이 갭 구간
        Span responseBuildSpan = tracer.nextSpan().name("coupon.issue.response-build").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(responseBuildSpan)) {
            couponMetrics.incrementIssueSuccess(couponId.toString());
            log.info("[CouponService] 쿠폰 발급 완료. couponId={}, userId={}", couponId, userId);
            return UserCouponResponse.from(userCoupon);
        } finally {
            responseBuildSpan.end();
        }
    }

    private void registerTxCommitSpan() {
        Span commitSpan = tracer.nextSpan().name("coupon.issue.tx-commit");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                commitSpan.start();
            }

            @Override
            public void afterCompletion(int status) {
                commitSpan.end();
            }
        });
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}
