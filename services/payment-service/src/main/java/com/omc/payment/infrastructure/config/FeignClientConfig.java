package com.omc.payment.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {

    // 내부 서비스 호출 시 gateway secret 헤더를 공통으로 주입
    @Bean
    public RequestInterceptor gatewaySecretRequestInterceptor(
            @Value("${gateway.secret}") String gatewaySecret
    ) {
        return template -> template.header("X-Gateway-Secret", gatewaySecret);
    }
}
