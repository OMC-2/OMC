package com.omc.order.application.service;


import com.omc.common.response.ApiResponse;
import com.omc.order.application.event.dto.PurchaseConfirmedEvent;
import com.omc.order.application.event.dto.RaffleWinnerSelectedEvent;
import com.omc.order.application.event.outbox.OutboxEventRecorder;
import com.omc.order.domain.entity.Order;
import com.omc.order.domain.enums.CancelReason;
import com.omc.order.domain.enums.OrderStatus;
import com.omc.order.domain.exception.OrderNotFoundException;
import com.omc.order.domain.repository.OrderRepository;
import com.omc.order.infrastructure.client.ProductFeignClient;
import com.omc.order.infrastructure.client.dto.ProductResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//OrderService 단위 테스트 (Mockito)
//의존성 (OutboxEventRecorder, ProductFeignClient, OrderRepository)을 모두 mock
//jacoco 커버리지 대상(application.service.*)이므로 전 메서드 + 핵심 분기 커버

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  OutboxEventRecorder outboxRecorder;
  @Mock
  ProductFeignClient productFeignClient;
  @Mock
  OrderRepository orderRepository;
  @InjectMocks
  OrderService orderService;

  private PurchaseConfirmedEvent dropEvent() {
    return PurchaseConfirmedEvent.builder()
        .eventId("evt-drop-1")
        .orderId(UUID.randomUUID())
        .dropId(UUID.randomUUID())
        .userId(UUID.randomUUID())
        .productId(UUID.randomUUID())
        .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
        .build();
  }

  private RaffleWinnerSelectedEvent raffleEvent() {
    return RaffleWinnerSelectedEvent.builder()
        .eventId("evt-raffle-1")
        .entryId(UUID.randomUUID())
        .raffleId(UUID.randomUUID())
        .productId(UUID.randomUUID())
        .billingKeyId(UUID.randomUUID())
        .couponId(UUID.randomUUID())
        .originalAmount(10_000L)
        .discountAmount(2_000L)
        .finalAmount(8_000L)
        .build();
  }

  private Order pendingDropOrder(UUID orderId) {
    return Order.createDropOrder(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10_000L, "idem");
  }

  /**
   * outboxRecorder.record(...)에 넘긴 payloadFactory 람다를 캡처해 실행한다.
   * 람다 본문(OrderCreatedEvent 등 payload 빌더)의 커버리지를 확보하기 위함.
   * mock은 record를 호출만 기록하고 람다를 실행하지 않으므로, 여기서 직접 apply한다.
   */
  private Object captureAndRunPayloadFactory() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Function<String, Object>> factoryCaptor =
        ArgumentCaptor.forClass(Function.class);
    verify(outboxRecorder).record(anyString(), any(), anyString(), anyString(), factoryCaptor.capture());
    return factoryCaptor.getValue().apply("generated-event-id");
  }

  @Nested
  @DisplayName("드롭 주문 생성")
  class CreateDropOrder {

    @Test
    @DisplayName("상품 조회 성공 시 주문 저장 + order.created 아웃박스 적재")
    void success() {
      PurchaseConfirmedEvent event = dropEvent();
      when(productFeignClient.getProductDetail(event.productId()))
          .thenReturn(ApiResponse.success(new ProductResponse(10_000L)));

      orderService.createDropOrder(event);

      //주문이 PK = payload.orderId로 저장됐는가
      ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
      verify(orderRepository).save(orderCaptor.capture());
      Order saved = orderCaptor.getValue();
      assertThat(saved.getOrderId()).isEqualTo(event.orderId());
      assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
      assertThat(saved.getOriginalAmount()).isEqualTo(10_000L);

      //order.created 아웃박스 적재 + 람다 실행 → OrderCreatedEvent(DROP) 생성 커버
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Function<String, Object>> factoryCaptor =
          ArgumentCaptor.forClass(Function.class);
      verify(outboxRecorder).record(
          eq("ORDER"), eq(event.orderId()), eq("ORDER_CREATED"), eq("order.created"), factoryCaptor.capture());
      Object payload = factoryCaptor.getValue().apply("generated-event-id");
      assertThat(payload).hasFieldOrPropertyWithValue("orderType", "DROP");
      assertThat(payload).hasFieldOrPropertyWithValue("originalAmount", 10_000L);
      assertThat(payload).hasFieldOrPropertyWithValue("billingKeyId", null);
    }


    @Test
    @DisplayName("상품 응답이 null이면 예외 + 주문 미지정")
    void nullResponse() {
      PurchaseConfirmedEvent event = dropEvent();
      when(productFeignClient.getProductDetail(event.productId())).thenReturn(null);

      assertThatThrownBy(() -> orderService.createDropOrder(event))
          .isInstanceOf(IllegalStateException.class);

      verify(orderRepository, never()).save(any());
      verify(outboxRecorder, never()).record(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("상품 데이터가 null이면 예외 + 주문 미지정")
    void nullData() {
      PurchaseConfirmedEvent event = dropEvent();
      when(productFeignClient.getProductDetail(event.productId())).thenReturn(ApiResponse.success(null));

      assertThatThrownBy(() -> orderService.createDropOrder(event)).isInstanceOf(IllegalStateException.class);

      verify(orderRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("래플 주문 생성")
  class CreateRaffleOrder {

    @Test
    @DisplayName("주문 저장 + order.created 적재 (product 조회 없음)")
    void success() {
      RaffleWinnerSelectedEvent event = raffleEvent();

      orderService.createRaffleOrder(event);

      ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
      verify(orderRepository).save(orderCaptor.capture());
      Order saved = orderCaptor.getValue();
      assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
      assertThat(saved.getFinalAmount()).isEqualTo(8_000L);
      assertThat(saved.getEntryId()).isEqualTo(event.entryId());

      // 래플은 product 조회를 하지 않는다
      verify(productFeignClient, never()).getProductDetail(any());
      verify(outboxRecorder).record(
          eq("ORDER"), any(UUID.class), eq("ORDER_CREATED"), eq("order.created"), any());
    }

    @Test
    @DisplayName("billingKeyId가 order.created payload에 String으로 전달된다")
    void billingKeyIdPropagated() {
      RaffleWinnerSelectedEvent event = raffleEvent();

      orderService.createRaffleOrder(event);

      // payloadFactory를 캡처해 실행, billingKeyId가 String으로 들어가는지 확인
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Function<String, Object>> factoryCaptor =
          ArgumentCaptor.forClass(Function.class);
      verify(outboxRecorder).record(anyString(), any(), anyString(), anyString(), factoryCaptor.capture());

      Object payload = factoryCaptor.getValue().apply("generated-event-id");
      assertThat(payload).hasFieldOrPropertyWithValue("billingKeyId", event.billingKeyId().toString());
    }

    @Test
    @DisplayName("billingKeyId가 null이면 payload billingKeyId도 null로 전달된다")
    void billingKeyIdNull() {
      RaffleWinnerSelectedEvent event = RaffleWinnerSelectedEvent.builder()
          .eventId("evt-raffle-nokey")
          .entryId(UUID.randomUUID())
          .raffleId(UUID.randomUUID())
          .userId(UUID.randomUUID())
          .productId(UUID.randomUUID())
          .billingKeyId(null)            // billingKeyId 없음 → 삼항 분기의 null 경로
          .couponId(UUID.randomUUID())
          .originalAmount(10_000L)
          .discountAmount(2_000L)
          .finalAmount(8_000L)
          .build();

      orderService.createRaffleOrder(event);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<Function<String, Object>> factoryCaptor =
          ArgumentCaptor.forClass(Function.class);
      verify(outboxRecorder).record(anyString(), any(), anyString(), anyString(), factoryCaptor.capture());

      Object payload = factoryCaptor.getValue().apply("generated-event-id");
      assertThat(payload).hasFieldOrPropertyWithValue("billingKeyId", null);
    }
  }

  @Nested
  @DisplayName("결제 완료 / 확정 / 취소 전이")
  class StateTransitions {

    @Test
    @DisplayName("markOrderPaid: PENDING_PAYMENT -> PAID")
    void markPaid() {
      UUID orderId = UUID.randomUUID();
      UUID paymentId = UUID.randomUUID();
      Order order = pendingDropOrder(orderId);
      when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

      orderService.markOrderPaid(orderId, paymentId);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
      assertThat(order.getPaymentId()).isEqualTo(paymentId);
    }

    @Test
    @DisplayName("confirmOrder: PAID -> CONFIRMED + order.confirmed 적재")
    void confirm() {
      UUID orderId = UUID.randomUUID();
      Order order = pendingDropOrder(orderId);
      order.markPaid(UUID.randomUUID()); // PAID로 만들어둠
      when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

      orderService.confirmOrder(orderId);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
      verify(outboxRecorder).record(
          eq("ORDER"), eq(orderId), eq("ORDER_CONFIRMED"), eq("order.confirmed"), any());
      // 람다(payload 빌더) 실행 → OrderConfirmedEvent 생성 커버
      Object payload = captureAndRunPayloadFactory();
      assertThat(payload).hasFieldOrPropertyWithValue("orderId", orderId);
    }

    @Test
    @DisplayName("cancelOrder: 결제 실패로 CANCELLED + order.cancelled 적재")
    void cancel() {
      UUID orderId = UUID.randomUUID();
      Order order = pendingDropOrder(orderId);
      when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

      orderService.cancelOrder(orderId, CancelReason.PAYMENT_FAILED);

      assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(order.getCancelReason()).isEqualTo(CancelReason.PAYMENT_FAILED);
      verify(outboxRecorder).record(
          eq("ORDER"), eq(orderId), eq("ORDER_CANCELLED"), eq("order.cancelled"), any());
      // 람다 실행 → OrderCancelledEvent 생성 커버 (reason 포함)
      Object payload = captureAndRunPayloadFactory();
      assertThat(payload).hasFieldOrPropertyWithValue("reason", "PAYMENT_FAILED");
    }

    @Test
    @DisplayName("존재하지 않는 주문 확정 시 OrderNotFoundException")
    void notFound() {
      UUID orderId = UUID.randomUUID();
      when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> orderService.confirmOrder(orderId))
          .isInstanceOf(OrderNotFoundException.class);

      verify(outboxRecorder, never()).record(anyString(), any(), anyString(), anyString(), any());
    }
  }

    @Nested
    @DisplayName("배송 / 환불")
    class ShippingAndRefund {

      @Test
      @DisplayName("startShipping: CONFIRMED -> SHIPPING + order.shipped 적재")
      void startShipping() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingDropOrder(orderId);
        order.markPaid(UUID.randomUUID());
        order.confirm(); // CONFIRMED
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.startShipping(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        verify(outboxRecorder).record(
            eq("ORDER"), eq(orderId), eq("ORDER_SHIPPED"), eq("order.shipped"), any());
        // 람다 실행 → OrderShippedEvent 생성 커버
        Object payload = captureAndRunPayloadFactory();
        assertThat(payload).hasFieldOrPropertyWithValue("orderId", orderId);
      }


      @Test
      @DisplayName("requestRefund: CONFIRMED -> REFUND_REQUESTED + refund.requested 적재")
      void requestRefund() {
        UUID orderId = UUID.randomUUID();
        Order order = pendingDropOrder(orderId);
        order.markPaid(UUID.randomUUID());
        order.confirm();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.requestRefund(orderId, CancelReason.USER_REQUESTED);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
        verify(outboxRecorder).record(
            eq("ORDER"), eq(orderId), eq("REFUND_REQUESTED"), eq("refund.requested"), any());
        // 람다 실행 → RefundRequestedEvent 생성 커버 (reason 포함)
        Object payload = captureAndRunPayloadFactory();
        assertThat(payload).hasFieldOrPropertyWithValue("reason", "USER_REQUESTED");
      }
    }
  }

