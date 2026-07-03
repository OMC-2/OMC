package com.omc.raffle.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.omc.raffle.infrastructure.client.dto.RegisterBillingKeyRequest;
import com.omc.raffle.infrastructure.client.dto.RegisterBillingKeyResponse;

/**
 * 결제 서비스(payment-service)와 통신하여 빌링키 신규 발급(Mock)을 수행하는 Feign Client입니다.
 */
@FeignClient(name = "payment-service", url = "${payment.service.url:http://payment-service:8085}", configuration = com.omc.raffle.infrastructure.config.FeignConfig.class, fallback = PaymentFeignClientFallback.class)
public interface PaymentFeignClient {

    /**
     * 프론트엔드가 없는 테스트/서버 간 통신 환경을 위해, 가짜 빌링키를 신규 발급합니다.
     * @param request (customerKey, authKey)
     * @return 발급된 가짜 빌링키 정보
     */
    @PostMapping(value = "/internal/v1/payments/billing-keys", consumes = "application/json")
    RegisterBillingKeyResponse registerBillingKey(@RequestBody RegisterBillingKeyRequest request);
}
