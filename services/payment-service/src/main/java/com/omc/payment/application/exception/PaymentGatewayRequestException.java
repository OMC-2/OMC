package com.omc.payment.application.exception;

import lombok.Getter;


@Getter
public class PaymentGatewayRequestException extends RuntimeException {
    /**
     * 에러 코드와 메시지를 포함한 정상 에러 응답 반환 예외
     */
    private final String providerCode;

    public PaymentGatewayRequestException(String providerCode, String message) {
        super(message);
        this.providerCode = providerCode;
    }
}
