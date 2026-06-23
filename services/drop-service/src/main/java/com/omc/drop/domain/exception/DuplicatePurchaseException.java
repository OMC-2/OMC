package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class DuplicatePurchaseException extends BusinessException {

    public DuplicatePurchaseException() {
        super(DropErrorCode.DROP_DUPLICATE_PURCHASE);
    }
}
