package com.omc.payment.application.exception;

public class PaymentGatewayConnectionException extends RuntimeException {
    /**
     * 통신 실패 예외
     */
    public PaymentGatewayConnectionException(String message,  Throwable cause) {
        super(message, cause);
    }
}
