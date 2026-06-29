package com.omc.raffle.domain.enums;

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
    RAFFLE_004(HttpStatus.BAD_REQUEST, "RAFFLE-004", "결제 수단(카드) 가승인에 실패했습니다."),
    RAFFLE_005(HttpStatus.BAD_REQUEST, "RAFFLE-005", "래플 추첨이 아직 진행되지 않았습니다."),
    RAFFLE_006(HttpStatus.INTERNAL_SERVER_ERROR, "RAFFLE-006", "추첨 스케줄러 실행 중 오류가 발생했습니다."),
    RAFFLE_007(HttpStatus.FORBIDDEN, "RAFFLE-007", "노쇼(미결제)로 인해 당분간 래플 응모가 제한된 사용자입니다."),
    RAFFLE_008(HttpStatus.INTERNAL_SERVER_ERROR, "RAFFLE-008", "레디스 캐시 작업 중 오류가 발생했습니다."),
    RAFFLE_009(HttpStatus.INTERNAL_SERVER_ERROR, "RAFFLE-009", "이벤트 메시지 처리 중 오류가 발생했습니다."),
    RAFFLE_010(HttpStatus.INTERNAL_SERVER_ERROR, "RAFFLE-010", "응모 내역 저장 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}