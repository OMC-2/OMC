package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

public class OutboxEventNotFailedException extends BusinessException {
    public OutboxEventNotFailedException() {
        super(ProductErrorCode.OUTBOX_EVENT_NOT_FAILED);
    }
}
