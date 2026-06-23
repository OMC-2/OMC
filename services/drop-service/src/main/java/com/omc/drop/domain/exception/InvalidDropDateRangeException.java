package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class InvalidDropDateRangeException extends BusinessException {
    public InvalidDropDateRangeException() {
        super(DropErrorCode.DROP_INVALID_DATE_RANGE);
    }
}
