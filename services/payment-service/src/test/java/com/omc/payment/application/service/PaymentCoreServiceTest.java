package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.ApiResponse;
import com.omc.payment.application.port.out.PaymentGatewayCommand;
import com.omc.payment.application.port.out.PaymentGatewayPort;
import com.omc.payment.application.port.out.PaymentGatewayResult;
import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.domain.enums.PaymentMethod;
import com.omc.payment.domain.enums.PaymentStatus;
import com.omc.payment.domain.enums.Provider;
import com.omc.payment.domain.enums.SalesType;
import com.omc.payment.domain.exception.PaymentErrorCode;
import com.omc.payment.domain.exception.PaymentGatewayConnectionException;
import com.omc.payment.domain.exception.PaymentGatewayRequestException;
import com.omc.payment.domain.repository.PaymentRepository;
import com.omc.payment.infrastructure.client.CouponServiceClient;
import com.omc.payment.infrastructure.client.CouponUserCouponResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 코어 서비스 테스트")
class PaymentCoreServiceTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();
    private static final UUID DROP_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID COUPON_ID = UUID.randomUUID();
    private static final UUID RAFFLE_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private PaymentOutboxService paymentOutboxService;
    @Mock private CouponServiceClient couponServiceClient;

    @InjectMocks
    private PaymentCoreService paymentCoreService;

    @Nested
    @DisplayName("일반 결제 승인")
    class ConfirmPayment {

        @Test
        @DisplayName("드롭 결제를 승인하고 완료 아웃박스를 적재한다")
        void confirmPayment_success() {
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGatewayPort.confirmPayment(any(PaymentGatewayCommand.Confirm.class)))
                    .willReturn(new PaymentGatewayResult.Confirm("결제 승인 아이디"));

            Payment payment = paymentCoreService.confirmPayment(
                    ORDER_ID,
                    DROP_ID,
                    PRODUCT_ID,
                    null,
                    USER_ID,
                    10000L,
                    0L,
                    10000L,
                    "결제 승인 아이디"
            );

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getProviderPaymentId()).isEqualTo("결제 승인 아이디");
            assertThat(payment.getDropId()).isEqualTo(DROP_ID);
            assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
            verify(paymentOutboxService).savePaymentCompleted(payment);
            verify(couponServiceClient, never()).getUserCoupon(any());
        }

        @Test
        @DisplayName("같은 주문의 결제가 이미 있으면 기존 결제를 반환한다")
        void confirmPayment_returnsExistingPayment() {
            Payment existingPayment = createPayment(SalesType.DROP, DROP_ID, null, null, null, 10000L, 0L);

            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.of(existingPayment));

            Payment result = paymentCoreService.confirmPayment(
                    ORDER_ID,
                    DROP_ID,
                    PRODUCT_ID,
                    null,
                    USER_ID,
                    10000L,
                    0L,
                    10000L,
                    "결제 승인 아이디"
            );

            assertThat(result).isSameAs(existingPayment);
            verify(paymentRepository, never()).save(any(Payment.class));
            verifyNoInteractions(paymentGatewayPort, paymentOutboxService, couponServiceClient);
        }

        @Test
        @DisplayName("쿠폰 없이 할인 금액이 있으면 예외가 발생한다")
        void confirmPayment_invalidCouponWithoutCouponId() {
            assertThatThrownBy(() -> paymentCoreService.confirmPayment(
                    ORDER_ID,
                    DROP_ID,
                    PRODUCT_ID,
                    null,
                    USER_ID,
                    10000L,
                    1000L,
                    9000L,
                    "결제 승인 아이디"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                            .isEqualTo(PaymentErrorCode.PAYMENT_INVALID_COUPON));
        }

        @Test
        @DisplayName("PG 요청이 거절되면 결제를 실패 처리하고 실패 아웃박스를 적재한다")
        void confirmPayment_gatewayRequestFailure() {
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGatewayPort.confirmPayment(any(PaymentGatewayCommand.Confirm.class)))
                    .willThrow(new PaymentGatewayRequestException("TOSS-400", "카드 승인이 거절되었습니다"));

            assertThatThrownBy(() -> paymentCoreService.confirmPayment(
                    ORDER_ID,
                    DROP_ID,
                    PRODUCT_ID,
                    null,
                    USER_ID,
                    10000L,
                    0L,
                    10000L,
                    "결제 승인 아이디"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> {
                        BusinessException businessException = (BusinessException) exception;
                        assertThat(businessException.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_FAILED);
                        assertThat(businessException).hasMessage("카드 승인이 거절되었습니다");
                    });

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentOutboxService).savePaymentFailed(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(paymentCaptor.getValue().getFailureCode()).isEqualTo("TOSS-400");
            assertThat(paymentCaptor.getValue().getFailureMessage()).isEqualTo("카드 승인이 거절되었습니다");
        }

        @Test
        @DisplayName("PG 연결이 실패하면 결제를 알 수 없음 상태로 처리한다")
        void confirmPayment_gatewayConnectionFailure() {
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(paymentGatewayPort.confirmPayment(any(PaymentGatewayCommand.Confirm.class)))
                    .willThrow(new PaymentGatewayConnectionException("게이트웨이 타임아웃", new RuntimeException("입출력 오류")));

            assertThatThrownBy(() -> paymentCoreService.confirmPayment(
                    ORDER_ID,
                    DROP_ID,
                    PRODUCT_ID,
                    null,
                    USER_ID,
                    10000L,
                    0L,
                    10000L,
                    "결제 승인 아이디"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                            .isEqualTo(PaymentErrorCode.PAYMENT_GATEWAY_CONNECTION_FAILED));

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.UNKNOWN);
            verify(paymentOutboxService, never()).savePaymentFailed(any(Payment.class));
            verify(paymentOutboxService, never()).savePaymentCompleted(any(Payment.class));
        }
    }

    @Nested
    @DisplayName("빌링키 결제 승인")
    class ConfirmBillingPayment {

        @Test
        @DisplayName("래플 빌링키 결제를 승인하고 쿠폰을 재검증한다")
        void confirmBillingPayment_success() {
            given(paymentRepository.findByOrderId(ORDER_ID)).willReturn(Optional.empty());
            given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(couponServiceClient.getUserCoupon(COUPON_ID))
                    .willReturn(ApiResponse.success(rateCoupon("RESERVED", "15", "3000")));
            given(paymentGatewayPort.confirmBillingPayment(any(PaymentGatewayCommand.ConfirmBilling.class)))
                    .willReturn(new PaymentGatewayResult.Confirm("빌링 결제 아이디"));

            Payment payment = paymentCoreService.confirmBillingPayment(
                    ORDER_ID,
                    ENTRY_ID,
                    RAFFLE_ID,
                    PRODUCT_ID,
                    COUPON_ID,
                    USER_ID,
                    "빌링키 아이디",
                    null,
                    30000L,
                    3000L,
                    27000L
            );

            assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getSalesType()).isEqualTo(SalesType.RAFFLE);
            assertThat(payment.getEntryId()).isEqualTo(ENTRY_ID);
            assertThat(payment.getRaffleId()).isEqualTo(RAFFLE_ID);
            assertThat(payment.getCouponId()).isEqualTo(COUPON_ID);

            ArgumentCaptor<PaymentGatewayCommand.ConfirmBilling> commandCaptor =
                    ArgumentCaptor.forClass(PaymentGatewayCommand.ConfirmBilling.class);
            verify(paymentGatewayPort).confirmBillingPayment(commandCaptor.capture());
            assertThat(commandCaptor.getValue().customerKey()).isNotBlank();
            verify(paymentOutboxService).savePaymentCompleted(payment);
        }
    }

    @Nested
    @DisplayName("결제 취소")
    class CancelPaymentByPaymentId {

        @Test
        @DisplayName("사용자는 본인 결제만 취소할 수 있다")
        void cancelPaymentByPaymentId_userOwnPayment() {
            Payment payment = createApprovedPayment(SalesType.DROP, DROP_ID, null, null, null, 10000L, 0L);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));
            given(paymentGatewayPort.cancelPayment(any(PaymentGatewayCommand.Cancel.class)))
                    .willReturn(new PaymentGatewayResult.Cancel("취소 아이디"));

            Payment canceledPayment = paymentCoreService.cancelPaymentByPaymentId(
                    PAYMENT_ID,
                    USER_ID,
                    "USER",
                    null,
                    "사용자 취소"
            );

            assertThat(canceledPayment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(canceledPayment.getCancellationCode()).isEqualTo(CancellationCode.USER_CANCEL);
            assertThat(canceledPayment.getProviderCancellationId()).isEqualTo("취소아이디");
            verify(paymentOutboxService).saveRefundDone(canceledPayment);
        }

        @Test
        @DisplayName("사용자가 본인 결제가 아니면 취소할 수 없다")
        void cancelPaymentByPaymentId_accessDenied() {
            Payment payment = createApprovedPayment(SalesType.DROP, DROP_ID, null, null, null, 10000L, 0L);
            given(paymentRepository.findById(PAYMENT_ID)).willReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentCoreService.cancelPaymentByPaymentId(
                    PAYMENT_ID,
                    OTHER_USER_ID,
                    "USER",
                    null,
                    "사용자 취소"
            ))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                            .isEqualTo(CommonErrorCode.ACCESS_DENIED));

            verify(paymentGatewayPort, never()).cancelPayment(any(PaymentGatewayCommand.Cancel.class));
            verify(paymentOutboxService, never()).saveRefundDone(any(Payment.class));
        }
    }

    private Payment createPayment(
            SalesType salesType,
            UUID dropId,
            UUID raffleId,
            UUID entryId,
            UUID couponId,
            Long originalAmount,
            Long discountAmount
    ) {
        Payment payment = Payment.create(
                ORDER_ID,
                dropId,
                raffleId,
                entryId,
                PRODUCT_ID,
                couponId,
                USER_ID,
                salesType,
                originalAmount,
                discountAmount,
                Provider.TOSS,
                PaymentMethod.CARD
        );
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        return payment;
    }

    private Payment createApprovedPayment(
            SalesType salesType,
            UUID dropId,
            UUID raffleId,
            UUID entryId,
            UUID couponId,
            Long originalAmount,
            Long discountAmount
    ) {
        Payment payment = createPayment(salesType, dropId, raffleId, entryId, couponId, originalAmount, discountAmount);
        payment.startConfirming();
        payment.approve("결제 승인 아이디");
        return payment;
    }

    private CouponUserCouponResponse rateCoupon(String status, String discountValue, String maxDiscountAmount) {
        return new CouponUserCouponResponse(
                COUPON_ID,
                UUID.randomUUID(),
                "정률 쿠폰",
                "RATE",
                new BigDecimal(discountValue),
                new BigDecimal(maxDiscountAmount),
                status,
                null
        );
    }
}
