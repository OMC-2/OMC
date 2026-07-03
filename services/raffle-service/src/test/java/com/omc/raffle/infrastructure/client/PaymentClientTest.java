package com.omc.raffle.infrastructure.client;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.math.BigDecimal;
import java.util.UUID;
import com.omc.raffle.infrastructure.client.dto.RegisterBillingKeyRequest;
import com.omc.raffle.infrastructure.client.dto.RegisterBillingKeyResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "payment.service.url=http://localhost:${wiremock.server.port}",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.cloud.openfeign.circuitbreaker.enabled=true"
})
@AutoConfigureWireMock(port = 0)
@DisplayName("Payment Feign Client 통합 테스트")
class PaymentFeignClientTest {

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Test
    @DisplayName("빌링키 신규 발급 요청을 정상적으로 처리할 수 있다.")
    void registerBillingKey_success() {
        // given
        String newBillingKeyId = "bk_" + UUID.randomUUID().toString();

        stubFor(post(urlPathEqualTo("/internal/v1/payments/billing-keys"))
                .withRequestBody(matchingJsonPath("$.customerKey", equalTo("")))
                .withRequestBody(matchingJsonPath("$.authKey", equalTo("")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("{\"billingKeyId\":\"" + newBillingKeyId + "\"}")));

        // when
        RegisterBillingKeyResponse response = paymentFeignClient.registerBillingKey(new RegisterBillingKeyRequest("", ""));

        // then
        assertThat(response).isNotNull();
        assertThat(response.billingKeyId()).isEqualTo(newBillingKeyId);
    }

    @Test
    @DisplayName("결제 서버가 500 에러를 반환하면 서킷 브레이커 fallback이 동작해 PaymentPreAuthFailedException이 발생한다.")
    void registerBillingKey_serverError() {
        // given
        stubFor(post(urlPathEqualTo("/internal/v1/payments/billing-keys"))
                .willReturn(aResponse()
                        .withStatus(500)));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.omc.raffle.domain.exception.PaymentPreAuthFailedException.class, () -> {
            paymentFeignClient.registerBillingKey(new RegisterBillingKeyRequest("", ""));
        });
    }

    @Test
    @DisplayName("결제 서버가 404를 반환하면 서킷 브레이커 fallback이 동작해 PaymentPreAuthFailedException이 발생한다.")
    void registerBillingKey_notFound() {
        // given
        stubFor(post(urlPathEqualTo("/internal/v1/payments/billing-keys"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(com.omc.raffle.domain.exception.PaymentPreAuthFailedException.class, () -> {
            paymentFeignClient.registerBillingKey(new RegisterBillingKeyRequest("", ""));
        });
    }
}
