package com.omc.product.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

@Tag("integration")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.cloud.openfeign.circuitbreaker.enabled=true"
)
@AutoConfigureMockMvc
@Testcontainers
abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("omc")
                    .withUsername("omc")
                    .withPassword("password");

    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);

    static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        postgres.start();
        kafka.start();
        redis.start();
        wireMock.start();

        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()
        ))) {
            admin.createTopics(List.of(
                    new NewTopic("payment.completed", 3, (short) 1)
            )).all().get();
        } catch (Exception e) {
            throw new RuntimeException("Kafka 토픽 생성 실패", e);
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                postgres.getJdbcUrl() + "?currentSchema=product_db");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("feign.drop-service.url",
                () -> "http://localhost:" + wireMock.port());
        registry.add("spring.kafka.listener.concurrency", () -> "3");
    }

    protected void resetWireMock() {
        wireMock.resetAll();
    }

    protected static final String GW_SECRET = "test-gateway-secret";

    protected void stubHasActiveDrop(java.util.UUID productId, boolean hasActiveDrop) {
        wireMock.stubFor(WireMock.get(WireMock.urlMatching(
                        "/internal/v1/drops/products/.*"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "success": true,
                                "status": 200,
                                "message": "OK",
                                "data": { "hasActiveDrop": %s }
                            }
                            """.formatted(hasActiveDrop))));
    }

    protected void stubDropServiceTimeout() {
        // Feign read-timeout(3초)보다 긴 5초 지연 -> 타임아웃 유발
        wireMock.stubFor(WireMock.get(WireMock.urlMatching(
                        "/internal/v1/drops/products/.*"))
                .willReturn(WireMock.aResponse()
                        .withFixedDelay(5000)
                        .withStatus(200)));
    }
}
