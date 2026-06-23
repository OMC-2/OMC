package com.omc.coupon.domain.exception;

import com.omc.common.exception.BusinessException;

public class CouponOutOfStockException extends BusinessException {
    public CouponOutOfStockException() {
        super(CouponErrorCode.COUPON_OUT_OF_STOCK);
    }
}
