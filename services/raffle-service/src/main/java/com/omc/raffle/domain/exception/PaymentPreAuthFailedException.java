package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class PaymentPreAuthFailedException extends BusinessException {
    public PaymentPreAuthFailedException(ErrorCode errorCode) {
        super(errorCode);
    }
    public PaymentPreAuthFailedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}