package com.omc.payment.application.port.out;

import java.util.UUID;

public final class PaymentGatewayResult {

    // 객체 생성 방어
    private PaymentGatewayResult() {}

    public record Confirm(
            String providerPaymentId
    ) {}

    public record RegisterBillingKey(
            String billingKeyID
    ) {}

    public record Cancel(
            String providerCancellationId
    ) {}
}
