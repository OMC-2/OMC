package com.omc.order.domain.exception;

import com.omc.common.exception.BusinessException;

//BusinessException을 상속하므로 GlobalExceptionHandler가 ErrorCode의 HttpStatus로 응답
// REFUND_NOT_ALLOWED -> 403, NOT_CONFIRMED -> 400 등
public class OrderStateException extends BusinessException {

  public OrderStateException(OrderErrorCode errorCode) {
    super(errorCode);
  }
}
