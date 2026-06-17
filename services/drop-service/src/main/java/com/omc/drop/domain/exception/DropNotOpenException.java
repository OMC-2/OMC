package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class DropNotOpenException extends BusinessException {
    public DropNotOpenException() {
        super(DropErrorCode.DROP_NOT_OPEN);
    }
}
