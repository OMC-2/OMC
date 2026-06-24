package com.omc.payment.infrastructure.consumer;

import com.omc.payment.application.event.dto.inbound.OrderCreatedEvent;
import com.omc.payment.application.event.dto.inbound.RefundRequestedEvent;
import com.omc.payment.application.event.dto.inbound.StockFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("결제 이벤트 필수값 검증 테스트")
class PaymentEventValidatorTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DROP_ID = UUID.randomUUID();
    private static final UUID RAFFLE_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();

    private final PaymentEventValidator paymentEventValidator = new PaymentEventValidator();

    @Nested
    @DisplayName("주문 생성 이벤트 검증")
    class OrderCreatedValidation {

        @Test
        @DisplayName("드롭 주문의 필수값이 모두 있으면 검증에 성공한다")
        void validateDropOrder() {
            OrderCreatedEvent event = orderCreatedEvent("DROP", DROP_ID, null, null, null);

            assertThatCode(() -> paymentEventValidator.validate(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("드롭 주문에 드롭 아이디가 없으면 검증에 실패한다")
        void validateDropOrderWithoutDropId() {
            OrderCreatedEvent event = orderCreatedEvent("DROP", null, null, null, null);

            assertThatThrownBy(() -> paymentEventValidator.validate(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("드롭 ID는 필수입니다");
        }

        @Test
        @DisplayName("래플 주문에 빌링키 아이디가 없으면 검증에 실패한다")
        void validateRaffleOrderWithoutBillingKeyId() {
            OrderCreatedEvent event = orderCreatedEvent("RAFFLE", null, RAFFLE_ID, ENTRY_ID, " ");

            assertThatThrownBy(() -> paymentEventValidator.validate(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("빌링키 ID는 필수입니다");
        }

        @Test
        @DisplayName("지원하지 않는 주문 유형이면 검증에 실패한다")
        void validateUnsupportedOrderType() {
            OrderCreatedEvent event = orderCreatedEvent("일반 주문", null, null, null, null);

            assertThatThrownBy(() -> paymentEventValidator.validate(event))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("지원하지 않는 주문 유형입니다: 일반 주문");
        }
    }

    @Test
    @DisplayName("환불 요청에 환불 사유가 없으면 검증에 실패한다")
    void validateRefundRequestWithoutReason() {
        RefundRequestedEvent event = new RefundRequestedEvent(
                UUID.randomUUID().toString(), ORDER_ID, USER_ID, null
        );

        assertThatThrownBy(() -> paymentEventValidator.validate(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불 사유는 필수입니다");
    }

    @Test
    @DisplayName("재고 차감 실패 이벤트에 주문 아이디가 없으면 검증에 실패한다")
    void validateStockFailureWithoutOrderId() {
        StockFailedEvent event = new StockFailedEvent(UUID.randomUUID().toString(), null);

        assertThatThrownBy(() -> paymentEventValidator.validate(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 ID는 필수입니다");
    }

    private OrderCreatedEvent orderCreatedEvent(
            String orderType,
            UUID dropId,
            UUID raffleId,
            UUID entryId,
            String billingKeyId
    ) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                ORDER_ID,
                USER_ID,
                orderType,
                dropId,
                PRODUCT_ID,
                raffleId,
                entryId,
                10000L,
                0L,
                10000L,
                null,
                billingKeyId
        );
    }
}
