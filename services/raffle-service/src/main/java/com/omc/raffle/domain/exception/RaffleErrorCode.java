package com.omc.raffle.domain.exception;

import com.omc.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RaffleErrorCode implements ErrorCode {

    RAFFLE_001(HttpStatus.NOT_FOUND, "RAFFLE-001", "해당 래플 이벤트를 찾을 수 없습니다."),
    RAFFLE_002(HttpStatus.BAD_REQUEST, "RAFFLE-002", "이미 래플에 응모하셨습니다."),
    RAFFLE_003(HttpStatus.BAD_REQUEST, "RAFFLE-003", "진행 중인 래플이 아닙니다."),
    RAFFLE_004(HttpStatus.BAD_REQUEST, "RAFFLE-004", "래플 추첨이 아직 진행되지 않았습니다."),
    RAFFLE_005(HttpStatus.INTERNAL_SERVER_ERROR, "RAFFLE-005", "추첨 스케줄러 실행 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}