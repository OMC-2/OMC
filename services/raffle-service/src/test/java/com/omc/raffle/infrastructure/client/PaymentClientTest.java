package com.omc.raffle.infrastructure.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.math.BigDecimal;
import java.util.UUID;
import com.omc.raffle.infrastructure.client.dto.PreAuthRequest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "payment.service.url=http://localhost:${wiremock.server.port}",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureWireMock(port = 0)
@DisplayName("Payment Feign Client 통합 테스트")
class PaymentFeignClientTest {

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Test
    @DisplayName("결제 가승인 요청을 정상적으로 처리할 수 있다.")
    void preAuth_success() {
        // given
        String billingKeyId = "bk_" + UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100);

        stubFor(post(urlPathEqualTo("/internal/v1/payments/pre-auth"))
                .withRequestBody(matchingJsonPath("$.billingKeyId", equalTo(billingKeyId)))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("100")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)));

        // when & then (no exception thrown)
        paymentFeignClient.preAuthCard(new PreAuthRequest(billingKeyId, amount));
    }

    @Test
    @DisplayName("결제 서버가 500 에러를 반환하면 FeignException이 발생한다.")
    void preAuth_serverError() {
        // given
        String billingKeyId = "bk_" + UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100);

        stubFor(post(urlPathEqualTo("/internal/v1/payments/pre-auth"))
                .willReturn(aResponse()
                        .withStatus(500)));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(feign.FeignException.InternalServerError.class, () -> {
            paymentFeignClient.preAuthCard(new PreAuthRequest(billingKeyId, amount));
        });
    }

    @Test
    @DisplayName("결제 서버가 404를 반환하면 FeignException.NotFound가 발생한다.")
    void preAuth_notFound() {
        // given
        String billingKeyId = "bk_" + UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100);

        // NOTE: Feign 기본 설정에는 커넥션 타임아웃이 없어 RetryableException 검증이 불가능합니다.
        // 타임아웃 테스트는 FeignConfig에서 timeout 설정 후 별도 진행 필요.
        // 대신 404 응답 처리를 검증합니다.
        stubFor(post(urlPathEqualTo("/internal/v1/payments/pre-auth"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(feign.FeignException.NotFound.class, () -> {
            paymentFeignClient.preAuthCard(new PreAuthRequest(billingKeyId, amount));
        });
    }
}

