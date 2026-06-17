package com.omc.raffle.domain.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RaffleErrorCode {

    RAFFLE_001("RAFFLE-001", HttpStatus.NOT_FOUND, "해당 래플 이벤트를 찾을 수 없습니다."),
    RAFFLE_002("RAFFLE-002", HttpStatus.BAD_REQUEST, "이미 래플에 응모하셨습니다."),
    RAFFLE_003("RAFFLE-003", HttpStatus.BAD_REQUEST, "진행 중인 래플이 아닙니다."),
    RAFFLE_004("RAFFLE-004", HttpStatus.BAD_REQUEST, "래플 추첨이 아직 진행되지 않았습니다."),
    RAFFLE_005("RAFFLE-005", HttpStatus.INTERNAL_SERVER_ERROR, "추첨 스케줄러 실행 중 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
