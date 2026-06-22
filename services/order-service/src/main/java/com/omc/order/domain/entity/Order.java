package com.omc.order.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.order.domain.enums.CancelReason;
import com.omc.order.domain.enums.OrderStatus;
import com.omc.order.domain.enums.OrderType;
import com.omc.order.domain.exception.OrderErrorCode;
import com.omc.order.domain.exception.OrderStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Order extends BaseEntity implements Persistable<UUID> {

  @Id
  @Column(name = "order_id")
  private UUID orderId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "drop_id")
  private UUID dropId;

  @Column(name = "raffle_id")
  private UUID raffleId;

  @Column(name = "entry_id")
  private UUID entryId;

  @Column(name = "payment_id")
  private UUID paymentId;

  @Column(name = "applied_coupon_id")
  private UUID appliedCouponId;

  @Enumerated(EnumType.STRING)
  @Column(name = "order_type", nullable = false, length = 50)
  private OrderType orderType;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 50)
  private OrderStatus status;

  @Column(name = "original_amount", nullable = false, columnDefinition = "bigint")
  private Long originalAmount;

  @Column(name = "discount_amount", nullable = false, columnDefinition = "bigint")
  private Long discountAmount;

  @Column(name = "final_amount", nullable = false, columnDefinition = "bigint")
  private Long finalAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "cancel_reason", length = 50)
  private CancelReason cancelReason;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "refundable_until")
  private LocalDateTime refundableUntil;

  @Column(name = "paid_at")
  private LocalDateTime paidAt;

  @Column(name = "confirmed_at")
  private LocalDateTime confirmedAt;

  @Column(name = "shipped_at")
  private LocalDateTime shippedAt;

  @Column(name = "delivered_at")
  private LocalDateTime deliveredAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Column(name = "refunded_at")
  private LocalDateTime refundedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Integer version;

  @Column(name = "idempotency_key", length = 255)
  private String idempotencyKey;

  @Builder(access = AccessLevel.PRIVATE)
  private Order(UUID orderId, UUID userId, UUID productId, UUID dropId, UUID raffleId, UUID entryId, UUID paymentId,
                UUID appliedCouponId, OrderType orderType, Integer quantity, OrderStatus status, Long originalAmount,
                Long discountAmount, Long finalAmount, LocalDateTime expiresAt, String idempotencyKey) {
    this.orderId = orderId;
    this.userId = userId;
    this.productId = productId;
    this.dropId = dropId;
    this.raffleId = raffleId;
    this.entryId = entryId;
    this.paymentId = paymentId;
    this.appliedCouponId = appliedCouponId;
    this.orderType = orderType;
    this.quantity = quantity;
    this.status = status;
    this.originalAmount = originalAmount;
    this.discountAmount = discountAmount;
    this.finalAmount = finalAmount;
    this.expiresAt = expiresAt;
    this.idempotencyKey = idempotencyKey;
  }

  //Persistable 구현, SELECT-before-INSERT 회피
  @Override
  public UUID getId() {
    return this.orderId;
  }

  @Override
  public boolean isNew() {
    return this.orderId == null;
  }

  //[DROP] 드롭이 생성한 orderId를 그대로 PK 사용
  public static Order createDropOrder(UUID orderId, UUID userId, UUID productId, UUID dropId, Long originalAmount, String idempotencyKey) {
    return Order.builder()
        .orderId(orderId)
        .userId(userId)
        .productId(productId)
        .dropId(dropId)
        .orderType(OrderType.DROP)
        .quantity(1)
        .status(OrderStatus.PENDING_PAYMENT)
        .originalAmount(originalAmount)
        .discountAmount(0L)
        .finalAmount(originalAmount)
        .expiresAt(LocalDateTime.now().plusMinutes(10)) //10분 타임아웃
        .idempotencyKey(idempotencyKey)
        .build();
  }

  //[RAFFLE] order-first: 당첨 수신 시 PENDING_PAYMENT로 생성 (order.created -> payment capture -> payment.completed
  public static Order createRaffleOrder(UUID orderId, UUID userId, UUID productId, UUID raffleId, UUID entryId, UUID appliedCouponId, Long originalAmount, Long discountAmount, Long finalAmount, String idempotencyKey) {
    return Order.builder()
        .orderId(orderId)
        .userId(userId)
        .productId(productId)
        .raffleId(raffleId)
        .entryId(entryId)
        .appliedCouponId(appliedCouponId)
        .orderType(OrderType.RAFFLE)
        .quantity(1)
        .status(OrderStatus.PENDING_PAYMENT) // 즉시 CONFIRMED -> PENDING_PAYMENT
        .originalAmount(originalAmount)
        .discountAmount(discountAmount)
        .finalAmount(finalAmount)
        .expiresAt(LocalDateTime.now().plusMinutes(10)) //capture 타임아웃
        .idempotencyKey(idempotencyKey)
        .build();
  }

  //상태 전이 캡슐화

  //[결제 완료] PENDING_PAYMENT -> PAID (payment.completed 수신, 재고 확정 차감 대기)
  public void markPaid(UUID paymentId) {
    if(this.status != OrderStatus.PENDING_PAYMENT) {
      throw new OrderStateException(OrderErrorCode.NOT_PENDING_PAYMENT);
    }
    this.status = OrderStatus.PAID;
    this.paymentId = paymentId;
    this.paidAt = LocalDateTime.now();
  }


  //[재고 확정 차감 완료] PAID -> CONFIRMED (stock.deducted 수신)
  public void confirm() {
    if (this.status != OrderStatus.PAID) {
      throw new OrderStateException(OrderErrorCode.INVALID_ORDER_STATE);
    }
    this.status = OrderStatus.CONFIRMED;
    this.confirmedAt = LocalDateTime.now();
  }

  //[취소] 결제 실패 / 홀드 만료 / 재고 차감 실패 등 -> CANCELLED
  public void cancel(CancelReason reason) {
    if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.DELIVERED || this.status == OrderStatus.REFUNDED) {
      throw new OrderStateException(OrderErrorCode.ORDER_ALREADY_CANCELLED);
    }
    this.status = OrderStatus.CANCELLED;
    this.cancelReason = reason;
    this.cancelledAt = LocalDateTime.now();
  }

  // [환불 요청] CONFIRMED -> REFUND REQUESTED (사용자 직접 환불)
  public void requestRefund(CancelReason reason) {
    if (this.status != OrderStatus.CONFIRMED) {
      throw new OrderStateException(OrderErrorCode.REFUND_NOT_ALLOWED);
    }
    this.status = OrderStatus.REFUND_REQUESTED;
    this.cancelReason = reason;
  }


  //[배송 시작] CONFIRMED -> SHIPPING (배송 스케줄러)
  public void startShipping() {
    if (this.status != OrderStatus.CONFIRMED) {
      throw new OrderStateException(OrderErrorCode.NOT_CONFIRMED);
    }
    this.status = OrderStatus.SHIPPING;
    this.shippedAt = LocalDateTime.now();
  }

  //[배송 완료] SHIPPING -> DELIVERED
  public void completeDelivery() {
    if (this.status != OrderStatus.SHIPPING) {
      throw new OrderStateException(OrderErrorCode.NOT_SHIPPING);
    }
    this.status = OrderStatus.DELIVERED;
    this.deliveredAt = LocalDateTime.now();
  }
}
