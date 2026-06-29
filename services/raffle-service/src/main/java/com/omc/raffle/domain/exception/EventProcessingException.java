package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class EventProcessingException extends BusinessException {
    public EventProcessingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
