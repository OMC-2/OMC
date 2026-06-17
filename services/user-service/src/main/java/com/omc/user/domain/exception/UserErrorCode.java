package com.omc.user.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // 001~099: 사용자 기본
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER-002", "이미 존재하는 사용자입니다."),
    USER_NOT_APPROVED(HttpStatus.FORBIDDEN, "USER-003", "승인되지 않은 사용자입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "USER-004", "비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "USER-005", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "USER-006", "만료된 토큰입니다."),

    // 100~199: 배송지
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-101", "배송지를 찾을 수 없습니다."),
    ADDRESS_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "USER-102", "배송지는 최대 10개까지 등록 가능합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
