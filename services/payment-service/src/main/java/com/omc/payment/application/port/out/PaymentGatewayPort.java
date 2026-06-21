package com.omc.payment.application.port.out;

public interface PaymentGatewayPort {
    PaymentGatewayResult.Confirm confirmPayment(PaymentGatewayCommand.Confirm command);

    PaymentGatewayResult.RegisterBillingKey registerBillingKey(PaymentGatewayCommand.RegisterBillingKey command);

    // Toss 빌링키 자동 결제
    PaymentGatewayResult.Confirm confirmBillingPayment(PaymentGatewayCommand.ConfirmBilling command);

    PaymentGatewayResult.Cancel cancelPayment(PaymentGatewayCommand.Cancel command);
}
