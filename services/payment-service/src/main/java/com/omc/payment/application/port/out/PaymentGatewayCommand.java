package com.omc.payment.application.port.out;

import java.util.UUID;

public final class PaymentGatewayCommand {

    // 객체 생성 방어
    private PaymentGatewayCommand() {}

    public record Confirm(
            UUID orderId,
            UUID amount
    ) {}

    public record RegisterBillingKey(
            String customerKey, // 빌링키 식별용
            String authKey // 빌링키 발급용 승인키
    ) {}

    public record Cancel(
            String providerPaymentId,
            Long amount
    ) {}
}
