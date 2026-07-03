package com.omc.product.domain.exception;

import com.omc.common.exception.BusinessException;

/**
 * confirmDeduct()의 동시성 방어막(Semaphore)에서 일정 시간 내 허가를 못 받았을 때 발생
 * Kafka 리스너까지 예외가 전파되어 재시도 (→ 반복 실패 시 DLT)로 이어지도록 의도적으로
 * catch하지 않고 던짐 (InventoryConcurrentHttpBypassLoadTest로 검증된 완화 조치 —
 * 근본 원인이 아니라 "전멸" 대신 "안전하게 재시도"로 바꾸기 위한 장치)
 */
public class DeductionBusyException extends BusinessException {
    public DeductionBusyException() {
        super(ProductErrorCode.DEDUCTION_BUSY);
    }
}
