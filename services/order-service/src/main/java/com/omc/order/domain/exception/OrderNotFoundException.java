package com.omc.order.domain.exception;

import com.omc.common.exception.BusinessException;

public class OrderNotFoundException extends BusinessException {
  public OrderNotFoundException() {
    super(OrderErrorCode.ORDER_NOT_FOUND);
  }
}
