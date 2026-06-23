package com.omc.payment.infrastructure.adapter;

import com.omc.payment.application.port.out.PaymentGatewayCommand;
import com.omc.payment.application.port.out.PaymentGatewayPort;
import com.omc.payment.application.port.out.PaymentGatewayResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.pg.mode", havingValue = "test")
public class TestPaymentAdapter implements PaymentGatewayPort {
    /*
    * 외부 PG 호출을 제외한 테스트 환경
    * */

    // 외부 PG 호출 없이 즉시 승인 처리
    @Override
    public PaymentGatewayResult.Confirm confirmPayment(PaymentGatewayCommand.Confirm command) {
        return new PaymentGatewayResult.Confirm("test-payment-" + command.orderId());
    }

    // 가짜 빌링키 발급 처리
    @Override
    public PaymentGatewayResult.RegisterBillingKey registerBillingKey(PaymentGatewayCommand.RegisterBillingKey command) {
        return new PaymentGatewayResult.RegisterBillingKey("test-billing-" + command.customerKey());
    }

    // 빌링키 자동 결제 즉시 승인 처리
    @Override
    public PaymentGatewayResult.Confirm confirmBillingPayment(PaymentGatewayCommand.ConfirmBilling command) {
        return new PaymentGatewayResult.Confirm("test-billing-payment-" + command.orderId());
    }

    // 결제 취소 즉시 성공 처리
    @Override
    public PaymentGatewayResult.Cancel cancelPayment(PaymentGatewayCommand.Cancel command) {
        return new PaymentGatewayResult.Cancel("test-cancel-" + command.providerPaymentId());
    }
}
