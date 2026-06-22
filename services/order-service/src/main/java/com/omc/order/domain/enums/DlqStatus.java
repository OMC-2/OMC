package com.omc.order.domain.enums;

//FAILED : 재시도(backoff) 소진 후에도 처리 실패하여 적재된 상태, 관리자 개입 대기
//RESOLVED : 관리자가 원본 토픽으로 재발행하여 처리를 위임한 상태(실제 비즈니스 성공 여부 : 재발행된 메시지를 받은 consumer가 책임진다
public enum DlqStatus {
  FAILED,
  RESOLVED
}
