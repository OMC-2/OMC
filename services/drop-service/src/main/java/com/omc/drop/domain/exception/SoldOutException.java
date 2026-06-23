package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class SoldOutException extends BusinessException {

    public SoldOutException() {
        super(DropErrorCode.DROP_SOLD_OUT);
    }
}
