package com.omc.order.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER-001", "주문을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
