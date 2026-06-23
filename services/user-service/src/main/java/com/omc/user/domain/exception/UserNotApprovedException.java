package com.omc.user.domain.exception;

import com.omc.common.exception.BusinessException;

public class UserNotApprovedException extends BusinessException {
    public UserNotApprovedException() {
        super(UserErrorCode.USER_NOT_APPROVED);
    }
}
