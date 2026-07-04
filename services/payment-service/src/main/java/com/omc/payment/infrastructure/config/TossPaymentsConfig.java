package com.omc.payment.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "payment.pg.mode", havingValue = "toss", matchIfMissing = true)
public class TossPaymentsConfig {
    /*
    * Toss 전용 RestClient 통신 방식 적용
    * */
    @Bean
    public RestClient tossPaymentRestClient(
            @Value("${payment.toss.base-url}") String baseUrl,
            @Value("${payment.toss.secret-key}") String secretKey,
            @Value("${payment.toss.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${payment.toss.read-timeout-ms:3000}") long readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeaders(headers -> headers.setBasicAuth(secretKey, ""))
                .build();
    }
}
