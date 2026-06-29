package com.omc.raffle.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.omc.raffle.infrastructure.client.PaymentFeignClient;

/**
 * 통합 테스트 베이스 클래스.
 *
 * 실제 PostgreSQL 및 Redis 컨테이너를 static으로 선언하여
 * 테스트 클래스 간 컨테이너를 재사용합니다 (한 번만 기동).
 *
 * 상속 방법:
 *   class MyTest extends IntegrationTestSupport { ... }
 *
 * 주의사항:
 *   - PaymentFeignClient는 Feign 외부 호출이므로 기본 @MockBean 처리
 *     (필요 시 하위 테스트에서 @MockBean으로 재정의 가능)
 *   - Docker Desktop이 실행 중이어야 합니다.
 */
@SpringBootTest
@Testcontainers
public abstract class IntegrationTestSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("raffle_test")
                    .withUsername("test")
                    .withPassword("test")
                    ;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7")
                    .withExposedPorts(6379)
                    .withTmpFs(Map.of("/data", "rw"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL 연결 정보 주입 (H2 대체)
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis 연결 정보 주입 (EmbeddedRedis 대체)
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());

        // Kafka 리스너 비활성화 (통합 테스트에서 Kafka 브로커 불필요)
        registry.add("spring.kafka.listener.auto-startup", () -> "false");

        // Eureka 비활성화
        registry.add("eureka.client.enabled", () -> "false");
    }

    // PaymentFeignClient는 외부 payment-service에 실제 요청을 보내므로 기본 Mock 처리
    // 하위 테스트에서 doNothing() 또는 doThrow()로 동작 제어
    @MockBean
    protected PaymentFeignClient paymentFeignClient;
}
