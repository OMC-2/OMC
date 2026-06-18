package com.omc.order.domain.exception;

import com.omc.common.exception.ErrorCode;

public class OrderStateException extends RuntimeException {

  private final ErrorCode errorCode;

  public OrderStateException(OrderErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }
}
