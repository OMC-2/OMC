package com.omc.product.infrastructure.client;

import com.omc.product.domain.exception.DropServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * DropInternalClient Fallback
 *
 * Drop Service 무응답 시 차단(예외 throw) 전략을 사용
 *
 * 차단을 선택한 이유:
 * Drop Service 과부하 상황(드롭 오픈 중 대량 트래픽)에서
 * fallback이 hasActiveDrop=false를 반환하면 진행 중인 드롭이 있는
 * 상품의 재고/정보가 수정될 수 있어 데이터 정합성이 깨짐
 * 관리자 작업의 일시적 중단은 운영 불편 수준이지만,
 * 데이터 정합성 손상은 서비스 신뢰성에 직접 영향을 줌
 */
@Slf4j
@Component
public class DropInternalClientFallback implements DropInternalClient {

    @Override
    public com.omc.common.response.ApiResponse<ActiveDropResponse> hasActiveDrop(UUID productId) {
        log.error("[DropInternalClientFallback] Drop Service 응답 없음. productId={}", productId);
        throw new DropServiceUnavailableException();
    }
}
