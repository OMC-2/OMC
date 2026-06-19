package com.omc.order.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.order.domain.enums.OrderStatus;
import com.omc.order.domain.enums.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Order extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
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

  @Column(name = "cancel_reason", length = 100)
  private String cancelReason;

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
  private Order(UUID userId, UUID productId, UUID dropId, UUID raffleId, UUID entryId, UUID paymentId,
                UUID appliedCouponId, OrderType orderType, Integer quantity, OrderStatus status, Long originalAmount,
                Long discountAmount, Long finalAmount, LocalDateTime expiresAt, String idempotencyKey) {
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

  //드롭 가생성용 정적 팩토리 메서드
  public static Order createDropOrder(UUID userId, UUID productId, UUID dropId, Long originalAmount, String idempotencyKey) {
    return Order.builder()
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

  //래플 당첨자 확정용 정적 팩토리 메서드
  public static Order createRaffleOrder(UUID userId, UUID productId, UUID raffleId, UUID entryId, UUID paymentId, UUID appliedCouponId, Long originalAmount, Long discountAmount, Long finalAmount, String idempotencyKey) {
    return Order.builder()
        .userId(userId)
        .productId(productId)
        .raffleId(raffleId)
        .entryId(entryId)
        .paymentId(paymentId)
        .appliedCouponId(appliedCouponId)
        .orderType(OrderType.RAFFLE)
        .quantity(1)
        .status(OrderStatus.CONFIRMED) //래플은 생성과 동시에 확정
        .originalAmount(originalAmount)
        .discountAmount(discountAmount)
        .finalAmount(finalAmount)
        .idempotencyKey(idempotencyKey)
        .build();
  }
}
