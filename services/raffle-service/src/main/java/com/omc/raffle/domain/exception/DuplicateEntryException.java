package com.omc.raffle.domain.exception;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.ErrorCode;

public class DuplicateEntryException extends BusinessException {
    public DuplicateEntryException(ErrorCode errorCode) {
        super(errorCode);
    }
    public DuplicateEntryException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}