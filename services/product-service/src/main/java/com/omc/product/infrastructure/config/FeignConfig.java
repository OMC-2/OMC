package com.omc.product.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * product-service -> drop-service Feign 호출 시
 * GatewayHeaderAuthFilter 통과를 위해 X-Gateway-Secret 헤더를 자동 주입한다.
 */
@Configuration
public class FeignConfig {

    @Value("${gateway.secret}")
    private String gatewaySecret;

    @Bean
    public RequestInterceptor gatewaySecretInterceptor() {
        return template -> template.header("X-Gateway-Secret", gatewaySecret);
    }
}
