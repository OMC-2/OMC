package com.omc.raffle.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * raffle -> payment 등 서버간 Feign 호출 시, payment 의 GatewayHeaderAuthFilter 를 통과하기 위해
 * 게이트웨이 시크릿 헤더(X-Gateway-Secret)를 자동으로 주입한다.
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
