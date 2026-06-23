package com.omc.order.infrastructure.client;

import com.omc.common.response.ApiResponse;
import com.omc.order.infrastructure.client.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "product-service", path = "/internal/v1/products")
public interface ProductFeignClient {

  @GetMapping("/{productId}")
  ApiResponse<ProductResponse> getProductDetail(@PathVariable("productId") UUID productId);
}
