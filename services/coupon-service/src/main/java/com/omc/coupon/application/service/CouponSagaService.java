package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.ProcessedEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.ProcessedEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponSagaService {

    private final UserCouponRepository userCouponRepository;
    private final ProcessedEventRepository processedEventRepository;

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

        userCoupon.get().confirm();
        log.info("[CouponSagaService] 쿠폰 사용 확정. orderId={}, userCouponId={}",
                orderId, userCoupon.get().getUserCouponId());
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

    private boolean markProcessed(String eventId, String topic) {
        try {
            processedEventRepository.save(ProcessedEvent.create(eventId, topic));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("[CouponSagaService] 중복 이벤트 스킵. eventId={}", eventId);
            return false;
        }
    }
}
