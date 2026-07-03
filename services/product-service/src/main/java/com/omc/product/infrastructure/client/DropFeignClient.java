package com.omc.product.infrastructure.client;

import com.omc.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "drop-service",
        url = "${feign.drop-service.url:}",
        fallbackFactory = DropFeignClientFallbackFactory.class
)
public interface DropFeignClient {

    @GetMapping("/internal/v1/drops/products/{productId}/active")
    ApiResponse<ActiveDropResponse> hasActiveDrop(@PathVariable UUID productId);
}
