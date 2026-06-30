package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class RaffleNotOpenException extends BusinessException {
    public RaffleNotOpenException(ErrorCode errorCode) {
        super(errorCode);
    }
    public RaffleNotOpenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}