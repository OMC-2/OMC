package com.omc.payment.presentation.dto.response;

import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.PaymentMethod;
import com.omc.payment.domain.enums.PaymentStatus;
import com.omc.payment.domain.enums.SalesType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        UUID entryId,
        UUID userId,
        SalesType salesType,
        Long finalAmount,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getEntryId(),
                payment.getUserId(),
                payment.getSalesType(),
                payment.getFinalAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentStatus(),
                payment.getRequestedAt(),
                payment.getApprovedAt()
        );
    }
}
