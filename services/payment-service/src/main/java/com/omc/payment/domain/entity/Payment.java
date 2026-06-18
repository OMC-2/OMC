package com.omc.payment.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.payment.domain.enums.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "p_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "order_id",  nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "sales_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private SalesType salesType;

    @PositiveOrZero
    @Column(name = "original_amount", nullable = false)
    private Long originalAmount;

    @PositiveOrZero
    @Column(name = "discount_amount")
    private Long discountAmount;

    @PositiveOrZero
    @Column(name = "final_amount", nullable = false)
    private Long finalAmount;

    @Column(name = "provider", nullable = false)
    @Enumerated(EnumType.STRING)
    private Provider provider;

    @Column(name = "provier_payment_id")
    private String providerPaymentId;

    @Column(name = "payment_method", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.READY;

    @Column(name = "failure_code")
    private String failureCode;

    @Lob
    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "cancellation_code")
    private CancellationCode cancellationCode;

    @Column(name = "cancelled_message")
    private String cancelledMessage;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static Payment create(
            UUID orderId,
            SalesType salesType,
            Long originalAmount,
            Long discountAmount,
            Provider provider,
            PaymentMethod paymentMethod
    ) {
        long resolvedOriginalAmount = Objects.requireNonNull(originalAmount, "원금액은 null일 수 없습니다.");
        long resolvedDiscountAmount = discountAmount == null ? 0L : discountAmount;

        if (resolvedOriginalAmount < 0) {
            throw new IllegalArgumentException("원금액은 0 이상이어야 합니다.");
        }
        if (resolvedDiscountAmount < 0) {
            throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
        }
        if (resolvedDiscountAmount > resolvedOriginalAmount) {
            throw new IllegalArgumentException("할인 금액은 원금액을 초과할 수 없습니다.");
        }

        return Payment.builder()
                .orderId(Objects.requireNonNull(orderId, "주문 ID는 null일 수 없습니다."))
                .salesType(Objects.requireNonNull(salesType, "판매 유형은 null일 수 없습니다."))
                .originalAmount(resolvedOriginalAmount)
                .discountAmount(resolvedDiscountAmount)
                .finalAmount(resolvedOriginalAmount - resolvedDiscountAmount)
                .provider(Objects.requireNonNull(provider, "결제 제공자는 null일 수 없습니다."))
                .paymentMethod(Objects.requireNonNull(paymentMethod, "결제 수단은 null일 수 없습니다."))
                .paymentStatus(PaymentStatus.READY)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(
            UUID paymentId,
            UUID orderId,
            SalesType salesType,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount,
            Provider provider,
            String providerPaymentId,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            String failureCode,
            String failureMessage,
            CancellationCode cancellationCode,
            String cancelledMessage,
            Long version,
            LocalDateTime requestedAt,
            LocalDateTime approvedAt,
            LocalDateTime failedAt,
            LocalDateTime canceledAt
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.salesType = salesType;
        this.originalAmount = originalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.cancellationCode = cancellationCode;
        this.cancelledMessage = cancelledMessage;
        this.version = version;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
        this.failedAt = failedAt;
        this.canceledAt = canceledAt;
    }
}
