package com.omc.payment.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.common.exception.BusinessException;
import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.domain.enums.PaymentMethod;
import com.omc.payment.domain.enums.PaymentStatus;
import com.omc.payment.domain.enums.Provider;
import com.omc.payment.domain.enums.SalesType;
import com.omc.payment.domain.exception.PaymentErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "entry_id", unique = true)
    private UUID entryId;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

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
    @Enumerated(EnumType.STRING)
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
            UUID entryId,
            UUID couponId,
            UUID userId,
            SalesType salesType,
            Long originalAmount,
            Long discountAmount,
            Provider provider,
            PaymentMethod paymentMethod
    ) {
        long resolvedOriginalAmount = Objects.requireNonNull(originalAmount, "원금은 null일 수 없습니다.");
        long resolvedDiscountAmount = discountAmount == null ? 0L : discountAmount;

        if (resolvedOriginalAmount < 0) {
            throw new IllegalArgumentException("원금은 0 이상이어야 합니다.");
        }
        if (resolvedDiscountAmount < 0) {
            throw new IllegalArgumentException("할인 금액은 0 이상이어야 합니다.");
        }
        if (resolvedDiscountAmount > resolvedOriginalAmount) {
            throw new IllegalArgumentException("할인 금액은 원금을 초과할 수 없습니다.");
        }

        SalesType resolvedSalesType = Objects.requireNonNull(salesType, "판매 유형은 null일 수 없습니다.");
        if (resolvedSalesType == SalesType.RAFFLE && entryId == null) {
            throw new IllegalArgumentException("래플 결제는 entryId가 필수입니다.");
        }

        return Payment.builder()
                .orderId(Objects.requireNonNull(orderId, "주문 ID는 null일 수 없습니다."))
                .entryId(entryId)
                .couponId(couponId)
                .userId(Objects.requireNonNull(userId, "유저 ID는 null일 수 없습니다."))
                .salesType(resolvedSalesType)
                .originalAmount(resolvedOriginalAmount)
                .discountAmount(resolvedDiscountAmount)
                .finalAmount(resolvedOriginalAmount - resolvedDiscountAmount)
                .provider(Objects.requireNonNull(provider, "결제 제공사는 null일 수 없습니다."))
                .paymentMethod(Objects.requireNonNull(paymentMethod, "결제 수단은 null일 수 없습니다."))
                .paymentStatus(PaymentStatus.READY)
                .requestedAt(LocalDateTime.now())
                .build();
    }

    // 결제 승인 요청 이벤트 처리
    public void startConfirming() {
        transitTo(PaymentStatus.CONFIRMING);
    }

    // PG 승인 성공 이벤트 반영
    public void approve(String providerPaymentId) {
        transitTo(PaymentStatus.PAID);
        this.providerPaymentId = Objects.requireNonNull(providerPaymentId, "PG 결제 ID는 null일 수 없습니다.");
        this.approvedAt = LocalDateTime.now();
        this.failedAt = null;
        this.failureCode = null;
        this.failureMessage = null;
    }

    // PG 승인 실패 이벤트 반영
    public void fail(String failureCode, String failureMessage) {
        transitTo(PaymentStatus.FAILED);
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.failedAt = LocalDateTime.now();
    }

    // PG 응답 지연 또는 확인 불가 이벤트 반영
    public void markUnknown() {
        transitTo(PaymentStatus.UNKNOWN);
    }

    // 결제 취소 또는 환불 이벤트 반영
    public void cancel(CancellationCode cancellationCode, String cancelledMessage) {
        transitTo(PaymentStatus.CANCELED);
        this.cancellationCode = cancellationCode;
        this.cancelledMessage = cancelledMessage;
        this.canceledAt = LocalDateTime.now();
    }

    private void transitTo(PaymentStatus targetStatus) {
        if (!paymentStatus.canChangeTo(targetStatus)) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_INVALID_STATUS,
                    "결제 상태를 " + paymentStatus + "에서 " + targetStatus + "로 변경할 수 없습니다."
            );
        }
        this.paymentStatus = targetStatus;
    }

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(
            UUID paymentId,
            UUID orderId,
            UUID entryId,
            UUID couponId,
            UUID userId,
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
        this.entryId = entryId;
        this.couponId = couponId;
        this.userId = userId;
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
