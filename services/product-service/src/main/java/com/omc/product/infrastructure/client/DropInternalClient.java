package com.omc.product.infrastructure.client;

import com.omc.common.response.ApiResponse;
import com.omc.product.infrastructure.client.dto.ActiveDropResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "drop-service")
public interface DropInternalClient {

    @GetMapping("/internal/v1/drops/products/{productId}/active")
    ApiResponse<ActiveDropResponse> hasActiveDrop(@PathVariable UUID productId);
}
