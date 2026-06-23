package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class DropNotFoundException extends BusinessException {
    public DropNotFoundException() {
        super(DropErrorCode.DROP_NOT_FOUND);
    }
}
