package com.omc.coupon.unit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.coupon.application.service.CouponSagaService;
import com.omc.coupon.application.service.ProcessedEventIdempotencyService;
import com.omc.coupon.domain.entity.Coupon;
import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.entity.UserCoupon;
import com.omc.coupon.domain.enums.UserCouponStatus;
import com.omc.coupon.domain.repository.OutboxEventRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponSagaServiceTest {

    @Mock private UserCouponRepository userCouponRepository;
    @Mock private ProcessedEventIdempotencyService processedEventIdempotencyService;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private CouponSagaService couponSagaService;

    private final UUID orderId = UUID.randomUUID();
    private final String eventId = "event-" + UUID.randomUUID();
    private final String topic = "payment.completed";

    // =========================================================================
    // [시나리오 1] payment.completed → RESERVED → USED 확정
    // =========================================================================

    @Test
    void confirmCoupon_success() throws Exception {
        // given
        UserCoupon userCouponMock = mock(UserCoupon.class);
        Coupon couponMock = mock(Coupon.class);
        doNothing().when(processedEventIdempotencyService).markProcessed(anyString(), anyString());
        given(userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.RESERVED))
                .willReturn(Optional.of(userCouponMock));
        given(userCouponMock.getUserCouponId()).willReturn(UUID.randomUUID());
        given(userCouponMock.getUserId()).willReturn(UUID.randomUUID());
        given(userCouponMock.getCoupon()).willReturn(couponMock);
        given(couponMock.getCouponId()).willReturn(UUID.randomUUID());
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        couponSagaService.confirmCoupon(eventId, topic, orderId);

        // then
        verify(userCouponMock).confirm();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    // =========================================================================
    // [시나리오 2] 이미 처리된 이벤트 ID → 비즈니스 로직 스킵 (멱등성)
    // =========================================================================

    @Test
    void confirmCoupon_idempotentSkip() {
        // given: PK 중복으로 DataIntegrityViolationException 발생 = 이미 처리된 이벤트
        willThrow(DataIntegrityViolationException.class)
                .given(processedEventIdempotencyService).markProcessed(anyString(), anyString());

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
        doNothing().when(processedEventIdempotencyService).markProcessed(anyString(), anyString());
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
        doNothing().when(processedEventIdempotencyService).markProcessed(anyString(), anyString());
        given(userCouponRepository.findByOrderIdAndStatus(orderId, UserCouponStatus.USED))
                .willReturn(Optional.of(userCouponMock));

        // when
        couponSagaService.restoreCouponFromUsed(eventId, "refund.done", orderId);

        // then
        verify(userCouponMock).restoreFromUsed(); // USED → AVAILABLE 호출 확인
    }
}
