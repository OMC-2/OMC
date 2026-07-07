package com.omc.payment.domain.entity;

import com.omc.common.util.UuidV7Generator;
import com.omc.payment.domain.enums.PaymentReconciliationResultType;
import com.omc.payment.domain.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "p_payment_reconciliation_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentReconciliationResult {

    @Id
    @Column(name = "reconciliation_result_id")
    private UUID reconciliationResultId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "provider_payment_id", nullable = false)
    private String providerPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_status", nullable = false)
    private PaymentStatus dbStatus;

    @Column(name = "pg_status")
    private String pgStatus;

    @Column(name = "db_amount", nullable = false)
    private Long dbAmount;

    @Column(name = "pg_amount")
    private Long pgAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false)
    private PaymentReconciliationResultType resultType;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    private PaymentReconciliationResult(
            Payment payment,
            String pgStatus,
            Long pgAmount,
            PaymentReconciliationResultType resultType
    ) {
        this.reconciliationResultId = UuidV7Generator.generate();
        this.paymentId = payment.getPaymentId();
        this.orderId = payment.getOrderId();
        this.providerPaymentId = payment.getProviderPaymentId();
        this.dbStatus = payment.getPaymentStatus();
        this.pgStatus = pgStatus;
        this.dbAmount = payment.getFinalAmount();
        this.pgAmount = pgAmount;
        this.resultType = resultType;
        this.checkedAt = LocalDateTime.now();
    }

    public static PaymentReconciliationResult create(
            Payment payment,
            String pgStatus,
            Long pgAmount,
            PaymentReconciliationResultType resultType
    ) {
        return new PaymentReconciliationResult(
                payment,
                pgStatus,
                pgAmount,
                resultType
        );
    }
}
