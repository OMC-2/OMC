package com.omc.order.infrastructure.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * order -> product 등 서버간 Feign 호출 시, product 의 GatewayHeaderAuthFilter 를 통과하기 위해
 * 게이트웨이 시크릿 헤더(X-Gateway-Secret)를 자동으로 주입한다.
 * 배경
 *  - GatewayHeaderAuthFilter 는 모든 요청(/internal 포함, swagger/actuator 제외)에서
 *    X-Gateway-Secret == gateway.secret 인지 검사하고, 아니면 403 을 반환한다.
 *  - 게이트웨이를 거치면 이 헤더가 자동으로 붙지만, Feign 직접 호출은 게이트웨이를 거치지 않으므로
 *    호출자가 직접 시크릿 헤더를 실어야 한다. order/product 가 동일한 gateway.secret 을 공유하므로 통과된다.
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
