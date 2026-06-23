package com.omc.notification.domain.exception;

import com.omc.common.exception.BusinessException;

public class NotificationNotFoundException extends BusinessException {

    public NotificationNotFoundException() {
        super(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
