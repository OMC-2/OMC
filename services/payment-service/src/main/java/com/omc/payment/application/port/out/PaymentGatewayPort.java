package com.omc.payment.application.port.out;

public interface PaymentGatewayPort {
    PaymentGatewayResult.Confirm confirmPayment(PaymentGatewayCommand.Confirm command);
    PaymentGatewayResult.RegisterBillingKey registerBillingKey(PaymentGatewayCommand.RegisterBillingKey command);
    PaymentGatewayResult.Cancel cancelPayment(PaymentGatewayCommand.Cancel command);
}
