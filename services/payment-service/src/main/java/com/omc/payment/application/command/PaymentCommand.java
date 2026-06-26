package com.omc.payment.application.command;

import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.domain.enums.SalesType;

import java.util.UUID;

public final class PaymentCommand {
    private PaymentCommand() {
    }

    public record Confirm(
            UUID orderId,
            UUID userId,
            SalesType salesType,
            UUID dropId,
            UUID entryId,
            UUID raffleId,
            UUID productId,
            UUID couponId,
            String billingKeyId,
            String customerKey,
            String providerPaymentId,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount
    ) {
    }

    public record Failure(
            UUID orderId,
            UUID userId,
            SalesType salesType,
            UUID dropId,
            UUID entryId,
            UUID raffleId,
            UUID productId,
            UUID couponId,
            String failureReason
    ) {
    }

    // 동기 호출 전용 Cancel
    public record CancelByPaymentId(
            UUID paymentId,
            UUID requesterId,
            String requesterRole,
            CancellationCode cancellationCode,
            String reason
    ) {
    }

    // 이벤트 처리 전용 Cancel
    public record CancelByOrderId(
            UUID orderId,
            CancellationCode cancellationCode,
            String reason
    ) {
    }

    public record RegisterBillingKey(
            String customerKey,
            String authKey
    ) {
    }
}
