package com.omc.drop.domain.exception;

import com.omc.common.exception.BusinessException;

public class PurchaseReservationFailedException extends BusinessException {
    public PurchaseReservationFailedException() {
        super(DropErrorCode.PURCHASE_RESERVATION_FAILED);
    }
}
