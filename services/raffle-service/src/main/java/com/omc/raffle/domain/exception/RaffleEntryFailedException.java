package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class RaffleEntryFailedException extends BusinessException {
    public RaffleEntryFailedException(ErrorCode errorCode) {
        super(errorCode);
    }
    public RaffleEntryFailedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}