package com.omc.drop.infrastructure.client;

import com.omc.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class ProductServiceClientFallbackFactory implements FallbackFactory<ProductServiceClient> {

    @Override
    public ProductServiceClient create(Throwable cause) {
        return new ProductServiceClient() {
            @Override
            public ApiResponse<InventorySnapshotResponse> getInventorySnapshot(UUID productId) {
                log.error("[ProductServiceClient] product-service 응답 없음. productId={}, cause={}",
                        productId, cause.getMessage());
                throw new ProductServiceUnavailableException(cause);
            }
        };
    }
}
