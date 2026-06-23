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
import com.omc.coupon.infrastructure.redis.CouponRedisRepository;
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
    private final ObjectMapper objectMapper;

    @Transactional
    public CouponResponse createCoupon(CouponCreateRequest request) {
        Coupon coupon = couponRepository.save(request.toEntity());
        couponRedisRepository.initStock(coupon.getCouponId().toString(), coupon.getTotalQuantity());
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
        Coupon coupon = findCoupon(couponId);

        if (!coupon.isIssuable()) {
            if (coupon.getExpiredAt().isBefore(java.time.LocalDateTime.now())) {
                throw new BusinessException(CouponErrorCode.COUPON_EXPIRED);
            }
            if (coupon.getStartedAt().isAfter(java.time.LocalDateTime.now())) {
                throw new BusinessException(CouponErrorCode.COUPON_NOT_STARTED);
            }
            throw new CouponOutOfStockException();
        }

        // Redis 이중 방어: 이미 발급 여부 확인
        if (couponRedisRepository.isAlreadyIssued(couponId.toString(), userId.toString())) {
            throw new CouponAlreadyIssuedException();
        }

        // Redis 원자적 재고 차감
        long remaining = couponRedisRepository.decrementStock(couponId.toString());
        if (remaining < 0) {
            couponRedisRepository.incrementStock(couponId.toString()); // 롤백
            throw new CouponOutOfStockException();
        }

        // DB 중복 체크 (Redis Set과 이중 방어)
        userCouponRepository.findByUserIdAndCoupon_CouponId(userId, couponId).ifPresent(uc -> {
            couponRedisRepository.incrementStock(couponId.toString()); // 롤백
            throw new CouponAlreadyIssuedException();
        });

        // UserCoupon 저장 + Outbox 저장 (같은 트랜잭션)
        UserCoupon userCoupon = userCouponRepository.save(
                UserCoupon.builder()
                        .userId(userId)
                        .coupon(coupon)
                        .expiredAt(coupon.getExpiredAt())
                        .build()
        );

        String payload = toJson(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "couponId", couponId.toString(),
                "userId", userId.toString()
        ));
        outboxEventRepository.save(OutboxEvent.create(
                "UserCoupon", userCoupon.getUserCouponId(), OutboxEventType.COUPON_ISSUED, payload
        ));

        // Redis 발급 목록에 추가 (중복 방지용 Set)
        couponRedisRepository.markIssued(couponId.toString(), userId.toString());

        log.info("[CouponService] 쿠폰 발급 완료. couponId={}, userId={}", couponId, userId);
        return UserCouponResponse.from(userCoupon);
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }
}
