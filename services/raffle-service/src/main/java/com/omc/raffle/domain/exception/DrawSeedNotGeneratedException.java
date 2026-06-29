package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class DrawSeedNotGeneratedException extends BusinessException {
    public DrawSeedNotGeneratedException(ErrorCode errorCode) {
        super(errorCode);
    }
    public DrawSeedNotGeneratedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}