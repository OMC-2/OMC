package com.omc.raffle.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.omc.raffle.infrastructure.client.dto.PreAuthRequest;

/**
 * 결제 서비스(payment-service)와 통신하여 카드 가승인 및 검증을 수행하는 Feign Client입니다.
 */
@FeignClient(name = "payment-service", url = "${payment.service.url:http://payment-service:8085}", configuration = com.omc.raffle.infrastructure.config.FeignConfig.class, fallback = PaymentFeignClientFallback.class)
public interface PaymentFeignClient {

    /**
     * 특정 빌링키를 사용하여 100원 등 최소 금액의 가승인을 요청하여 카드의 유효성을 검증합니다.
     * @param billingKeyId 결제 수단 빌링키 ID
     * @param amount 가승인 금액 (예: 100)
     * @return 가승인 성공 여부 또는 트랜잭션 결과 객체
     */
    @PostMapping("/internal/v1/payments/pre-auth")
    void preAuthCard(@RequestBody PreAuthRequest request);
}
