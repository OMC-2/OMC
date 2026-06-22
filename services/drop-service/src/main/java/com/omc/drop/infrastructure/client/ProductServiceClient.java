package com.omc.drop.infrastructure.client;

import com.omc.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @GetMapping("/internal/v1/products/{productId}/inventories/snapshot")
    ApiResponse<InventorySnapshotResponse> getInventorySnapshot(@PathVariable UUID productId);
}
