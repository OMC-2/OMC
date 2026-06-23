package com.omc.coupon.domain.exception;

import com.omc.common.exception.BusinessException;

public class UserCouponNotFoundException extends BusinessException {
    public UserCouponNotFoundException() {
        super(CouponErrorCode.USER_COUPON_NOT_FOUND);
    }
}
