package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class PenaltyActiveException extends BusinessException {
    public PenaltyActiveException(ErrorCode errorCode) {
        super(errorCode);
    }
    public PenaltyActiveException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}