package com.omc.product.infrastructure.client;

import com.omc.common.response.ApiResponse;
import com.omc.product.domain.exception.DropServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * DropInternalClient FallbackFactory
 *
 * FallbackFactory를 사용하는 이유:
 * Feign fallback 인터페이스에서 예외를 throw하면 일부 버전에서 null로 처리
 * FallbackFactory는 원인 예외(cause)를 받아 새 예외로 감싸 throw하는 것이 보장
 *
 * 차단 전략:
 * Drop Service 무응답 시 hasActiveDrop=false 반환 대신 예외를 throw
 * 드롭 오픈 중 Drop Service 과부하 상황에서 hasActiveDrop=false로 fallback하면
 * 진행 중인 드롭이 있는 상품의 재고/정보가 수정되어 데이터 정합성이 깨질 수 있음
 */
@Slf4j
@Component
public class DropInternalClientFallbackFactory implements FallbackFactory<DropInternalClient> {

    @Override
    public DropInternalClient create(Throwable cause) {
        return new DropInternalClient() {
            @Override
            public ApiResponse<ActiveDropResponse> hasActiveDrop(UUID productId) {
                log.error("[DropInternalClientFallback] Drop Service 응답 없음. productId={}, cause={}",
                        productId, cause.getMessage());
                throw new DropServiceUnavailableException();
            }
        };
    }
}
