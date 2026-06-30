package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class OutboxEventNotFoundException extends BusinessException {
    public OutboxEventNotFoundException() {
        super(ProductErrorCode.OUTBOX_EVENT_NOT_FOUND);
    }
}
