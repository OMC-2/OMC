package com.omc.coupon.unit.service;

import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.domain.entity.ProcessedEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.ProcessedEventRepository;
import com.omc.coupon.domain.repository.UserCouponRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponSagaServiceTest {

    @Mock private UserCouponRepository userCouponRepository;
    @Mock private ProcessedEventRepository processedEventRepository;

    @InjectMocks private CouponSagaService couponSagaService;

    private final UUID orderId = UUID.randomUUID();
    private final String eventId = "event-" + UUID.randomUUID();
    private final String topic = "payment.completed";

    // =========================================================================
    // [시나리오 1] payment.completed → RESERVED → USED 확정
    // =========================================================================

    @Test
    void confirmCoupon_success() {
        // given
        UserCoupon userCouponMock = mock(UserCoupon.class);
        given(processedEventRepository.save(any(ProcessedEvent.class)))
                .willReturn(mock(ProcessedEvent.class)); // 멱등성 기록 성공
        given(userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.RESERVED))
                .willReturn(Optional.of(userCouponMock));

        // when
        couponSagaService.confirmCoupon(eventId, topic, orderId);

        // then
        verify(userCouponMock).confirm(); // RESERVED → USED 호출 확인
    }

    // =========================================================================
    // [시나리오 2] 이미 처리된 이벤트 ID → 비즈니스 로직 스킵 (멱등성)
    // =========================================================================

    @Test
    void confirmCoupon_idempotentSkip() {
        // given: PK 중복으로 DataIntegrityViolationException 발생 = 이미 처리된 이벤트
        given(processedEventRepository.save(any(ProcessedEvent.class)))
                .willThrow(DataIntegrityViolationException.class);

        // when
        couponSagaService.confirmCoupon(eventId, topic, orderId);

        // then: 쿠폰 조회/변경 로직이 호출되지 않아야 함
        verify(userCouponRepository, never()).findByOrderIdAndStatus(any(), any());
    }

    // =========================================================================
    // [시나리오 3] payment.failed/hold.expired → RESERVED → AVAILABLE 복구
    // =========================================================================

    @Test
    void restoreCoupon_success() {
        // given
        UserCoupon userCouponMock = mock(UserCoupon.class);
        given(processedEventRepository.save(any(ProcessedEvent.class)))
                .willReturn(mock(ProcessedEvent.class));
        given(userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.RESERVED))
                .willReturn(Optional.of(userCouponMock));

        // when
        couponSagaService.restoreCoupon(eventId, "payment.failed", orderId);

        // then
        verify(userCouponMock).restore(); // RESERVED → AVAILABLE 호출 확인
    }

    // =========================================================================
    // [시나리오 4] refund.done (STOCK_DEDUCT_FAILED) → USED → AVAILABLE 복구
    // =========================================================================

    @Test
    void restoreCouponFromUsed_success() {
        // given
        UserCoupon userCouponMock = mock(UserCoupon.class);
        given(processedEventRepository.save(any(ProcessedEvent.class)))
                .willReturn(mock(ProcessedEvent.class));
        given(userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.USED))
                .willReturn(Optional.of(userCouponMock));

        // when
        couponSagaService.restoreCouponFromUsed(eventId, "refund.done", orderId);

        // then
        verify(userCouponMock).restoreFromUsed(); // USED → AVAILABLE 호출 확인
    }
}
