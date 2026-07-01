package com.omc.coupon.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.OutboxEventType;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.OutboxEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import com.omc.coupon.infrastructure.metrics.CouponMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.omc.common.util.UuidV7Generator;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSagaService {

    private final UserCouponRepository userCouponRepository;
    private final ProcessedEventIdempotencyService processedEventIdempotencyService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final CouponMetrics couponMetrics;

    /**
     * payment.completed → RESERVED → USED 확정
     */
    @Transactional
    public void confirmCoupon(String eventId, String topic, UUID orderId) {
        if (!markProcessed(eventId, topic)) return;

        Optional<UserCoupon> userCoupon =
                userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.RESERVED);

        if (userCoupon.isEmpty()) {
            log.info("[CouponSagaService] 선점된 쿠폰 없음 (쿠폰 미사용 주문). orderId={}", orderId);
            return;
        }

        UserCoupon uc = userCoupon.get();
        uc.confirm();

        UUID outboxEventId = UuidV7Generator.generate();
        String payload = toJson(Map.of(
                "eventId",  outboxEventId.toString(),
                "couponId", uc.getCoupon().getCouponId().toString(),
                "userId",   uc.getUserId().toString(),
                "orderId",  orderId.toString()
        ));
        outboxEventRepository.save(OutboxEvent.create(outboxEventId, "UserCoupon", uc.getUserCouponId(), OutboxEventType.COUPON_USED, payload));

        couponMetrics.incrementSagaConfirmed();
        log.info("[CouponSagaService] 쿠폰 사용 확정. orderId={}, userCouponId={}", orderId, uc.getUserCouponId());
    }

    /**
     * payment.failed / hold.expired → RESERVED → AVAILABLE 복구
     */
    @Transactional
    public void restoreCoupon(String eventId, String topic, UUID orderId) {
        if (!markProcessed(eventId, topic)) return;

        Optional<UserCoupon> userCoupon =
                userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.RESERVED);

        if (userCoupon.isEmpty()) {
            log.info("[CouponSagaService] 복구할 선점 쿠폰 없음. orderId={}", orderId);
            return;
        }

        userCoupon.get().restore();
        couponMetrics.incrementSagaRestored();
        log.info("[CouponSagaService] 쿠폰 복구 완료. orderId={}, userCouponId={}",
                orderId, userCoupon.get().getUserCouponId());
    }

    /**
     * refund.done (STOCK_DEDUCT_FAILED) → USED → AVAILABLE 복구 (Case B)
     */
    @Transactional
    public void restoreCouponFromUsed(String eventId, String topic, UUID orderId) {
        if (!markProcessed(eventId, topic)) return;

        Optional<UserCoupon> userCoupon =
                userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.USED);

        if (userCoupon.isEmpty()) {
            log.info("[CouponSagaService] 복구할 USED 쿠폰 없음. orderId={}", orderId);
            return;
        }

        userCoupon.get().restoreFromUsed();
        log.info("[CouponSagaService] 쿠폰 USED→AVAILABLE 복구 완료 (Case B). orderId={}, userCouponId={}",
                orderId, userCoupon.get().getUserCouponId());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }

    private boolean markProcessed(String eventId, String topic) {
        try {
            processedEventIdempotencyService.markProcessed(eventId, topic);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("[CouponSagaService] 중복 이벤트 스킵. eventId={}", eventId);
            return false;
        }
    }
}
