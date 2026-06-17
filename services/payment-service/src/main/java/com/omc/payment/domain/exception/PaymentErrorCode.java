package com.omc.payment.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT-002", "결제에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
