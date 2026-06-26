package com.omc.order.domain.entity;

import com.omc.order.domain.enums.CancelReason;
import com.omc.order.domain.enums.OrderStatus;
import com.omc.order.domain.enums.OrderType;
import com.omc.order.domain.exception.OrderStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

//Order 상태머신 단위 테스트
//Order 엔티티가 상태 전이를 캡슐화하고 있으므로 Spring 컨텍스트 없이 순수 단위 테스트로 검증
//핵심: 정상 전이 경로 + 잘못된 전이 차단(가드)
public class OrderStateMachineTest {

  private Order newDropOrder() {
    return Order.createDropOrder(
        UUID.randomUUID(),  //orderId(drop이 생성)
        UUID.randomUUID(),  //userId
        UUID.randomUUID(),  //productId
        UUID.randomUUID(),  //dropId
        10_000L,  //originalAmount
        "idem-key-drop"
    );
  }

  private Order newRaffleOrder() {
    return Order.createRaffleOrder(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        10_000L,
        2_000L,
        8_000L,
        "idem-key-raffle"
    );
  }

  @Nested
  @DisplayName("주문 생성")
  class Creation {

    @Test
    @DisplayName("드롭 주문은 PENDING_PAYMENT로 생성되고 할인 없이 금액이 동일하다")
    void createDropOrder() {
      Order order = newDropOrder();

      assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
      assertThat(order.getOrderType()).isEqualTo(OrderType.DROP);
      assertThat(order.getDiscountAmount()).isZero();
      assertThat(order.getOriginalAmount()).isEqualTo(order.getFinalAmount());
      assertThat(order.getExpiresAt()).isNotNull(); // 10분 타임아웃
    }

    @Test
    @DisplayName("래플 주문은 PENDING_PAYMENT로 생성되고 할인액이 반영된다")
    void createRaffleOrder() {
      Order order = newRaffleOrder();

      assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
      assertThat(order.getOrderType()).isEqualTo(OrderType.RAFFLE);
      assertThat(order.getDiscountAmount()).isEqualTo(2_000L);
      assertThat(order.getFinalAmount()).isEqualTo(8_000L);
      assertThat(order.getEntryId()).isNotNull();
    }
  }

  @Nested
  @DisplayName("정상 전이 경로")
  class HappyPath {

    @Test
    @DisplayName("PENDING_PAYMENT -> PAID -> CONFIRMED 순서로 전이된다")
    void pendingToPaidToConfirmed() {
      Order order = newDropOrder();
      UUID paymentId = UUID.randomUUID();

      order.markPaid(paymentId);
      assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
      assertThat(order.getPaymentId()).isEqualTo(paymentId);
      assertThat(order.getPaidAt()).isNotNull();

      order.confirm();
      assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
      assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMED -> SHIPPING -> DELIVERED 배송 흐름")
    void confirmedToShippingToDelivered() {
      Order order = newDropOrder();
      order.markPaid(UUID.randomUUID());
      order.confirm();

      order.startShipping();
      assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
      assertThat(order.getShippedAt()).isNotNull();

      order.completeDelivery();
      assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
      assertThat(order.getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("CONFIRMED -> REFUND_REQUESTED 사용자 환불 요청")
    void confirmedToRefundRequested() {
      Order order = newDropOrder();
      order.markPaid(UUID.randomUUID());
      order.confirm();

      order.requestRefund(CancelReason.USER_REQUESTED);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
      assertThat(order.getCancelReason()).isEqualTo(CancelReason.USER_REQUESTED);
    }
  }

  @Nested
  @DisplayName("잘못된 전이 차단 (가드)")
  class Guards {

    @Test
    @DisplayName("PENDING_PAYMENT를 건너뛰고 바로 confirm 하면 예외")
    void cannotConfirmBeforePaid() {
      Order order = newDropOrder(); // PENDING_PAYMENT

      assertThatThrownBy(order::confirm)
          .isInstanceOf(OrderStateException.class);
      assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT); // 상태 변하지 않음
    }

    @Test
    @DisplayName("이미 PAID인 주문을 다시 markPaid 하면 예외")
    void cannotMarkPaidTwice() {
      Order order = newDropOrder();
      order.markPaid(UUID.randomUUID());

      assertThatThrownBy(() -> order.markPaid(UUID.randomUUID()))
          .isInstanceOf(OrderStateException.class);
    }

    @Test
    @DisplayName("PAID 상태에서 바로 배송 시작하면 예외 (CONFIRMED 필요)")
    void cannotShipBeforeConfirmed() {
      Order order = newDropOrder();
      order.markPaid(UUID.randomUUID());

      assertThatThrownBy(order::startShipping)
          .isInstanceOf(OrderStateException.class);
    }

    @Test
    @DisplayName("PENDING_PAYMENT 상태에서 환불 요청하면 예외 (CONFIRMED만 가능)")
    void cannotRefundBeforeConfirmed() {
      Order order = newDropOrder();

      assertThatThrownBy(() -> order.requestRefund(CancelReason.USER_REQUESTED))
          .isInstanceOf(OrderStateException.class);
    }
  }

  @Nested
  @DisplayName("취소 전이")
  class Cancellation {

    @Test
    @DisplayName("PENDING_PAYMENT에서 결제 실패로 취소된다")
    void cancelFromPendingOnPaymentFailed() {
      Order order = newDropOrder();

      order.cancel(CancelReason.PAYMENT_FAILED);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(order.getCancelReason()).isEqualTo(CancelReason.PAYMENT_FAILED);
      assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING_PAYMENT에서 홀드 만료로 취소된다")
    void cancelFromPendingOnHoldExpired() {
      Order order = newDropOrder();

      order.cancel(CancelReason.HOLD_EXPIRED);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(order.getCancelReason()).isEqualTo(CancelReason.HOLD_EXPIRED);
    }

    @Test
    @DisplayName("PAID에서 재고 차감 실패로 취소된다")
    void cancelFromPaidOnStockFailed() {
      Order order = newDropOrder();
      order.markPaid(UUID.randomUUID());

      order.cancel(CancelReason.STOCK_DEDUCT_FAILED);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(order.getCancelReason()).isEqualTo(CancelReason.STOCK_DEDUCT_FAILED);
    }

    @Test
    @DisplayName("이미 취소된 주문을 다시 취소하면 예외")
    void cannotCancelTwice() {
      Order order = newDropOrder();
      order.cancel(CancelReason.PAYMENT_FAILED);

      assertThatThrownBy(() -> order.cancel(CancelReason.HOLD_EXPIRED))
          .isInstanceOf(OrderStateException.class);
    }

    @Test
    @DisplayName("CONFIRMED 상태에서도 취소 가능하다")
    void canCancelFromConfirmed() {
      Order order = newDropOrder();
      order.markPaid(UUID.randomUUID());
      order.confirm();

      assertThatCode(() -> order.cancel(CancelReason.STOCK_DEDUCT_FAILED))
          .doesNotThrowAnyException();
      assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }
  }
}
