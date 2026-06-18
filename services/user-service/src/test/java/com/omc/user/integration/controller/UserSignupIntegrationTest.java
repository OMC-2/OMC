package com.omc.user.integration.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omc.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원가입 API 통합 테스트 (POST /api/v1/users/signup)
 *
 * [인프라 구성]
 * - PostgreSQL: TestContainers로 실제 DB 컨테이너를 띄워 사용 (테스트 종료 시 자동 삭제)
 * - Keycloak: WireMock으로 대체 (Admin API 응답을 가짜로 stubbing)
 * - Spring Security: X-Gateway-Secret 헤더로 게이트웨이 인증 통과
 *
 * [테스트 격리]
 * - 컨테이너는 클래스 단위로 1번만 시작 (static @Container)
 * - 각 테스트 메서드 실행 전 @BeforeEach에서 DB 전체 삭제 + WireMock 초기화
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class UserSignupIntegrationTest {

    // 테스트 클래스 전체에서 컨테이너 1개 공유 (메서드마다 새로 띄우면 너무 느림)
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // @DynamicPropertySource보다 먼저 포트가 확정되어야 하므로 static 블록에서 시작
    static final WireMockServer wireMock;
    static {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    // TestContainers/WireMock이 동적으로 할당한 포트·URL을 Spring 설정에 주입
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("keycloak.server-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @AfterAll
    static void tearDown() {
        wireMock.stop();
    }

    // 각 테스트 메서드 실행 전: 이전 테스트 데이터 제거 + WireMock stub 초기화
    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        userRepository.deleteAll();
        stubAdminToken(); // 모든 테스트에서 Keycloak admin token 발급은 항상 성공 처리
    }

    /**
     * [시나리오 1] 정상 회원가입
     *
     * 흐름: 요청 → Keycloak 유저 생성 → Keycloak 역할 할당 → DB 저장
     *
     * 검증:
     * - 응답 201 Created
     * - 응답 body에 email, nickname, role(USER), userId 포함
     * - DB에 실제로 데이터가 저장되었는지 확인
     */
    @Test
    void signup_success_returns201() throws Exception {
        // given: Keycloak이 유저 생성과 역할 할당에 성공하도록 stub
        String keycloakUserId = UUID.randomUUID().toString();
        stubKeycloakCreateUser(keycloakUserId);
        stubKeycloakAssignRole(keycloakUserId);

        String body = """
                {
                    "email": "test@example.com",
                    "password": "password123",
                    "nickname": "testuser",
                    "slackId": "U12345"
                }
                """;

        // when & then: 요청 → 응답 검증
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("testuser"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty());

        // then: DB에 실제로 저장되었는지 직접 확인
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    /**
     * [시나리오 2] 중복 이메일 재가입 시도
     *
     * 흐름: 1차 가입(성공) → 동일 이메일로 2차 가입 시도(실패)
     *
     * 검증:
     * - 1차 가입: 201 Created
     * - 2차 가입: 409 Conflict + errorCode = USER-002
     */
    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        // given: Keycloak stub (1차 가입에서 사용)
        String keycloakUserId = UUID.randomUUID().toString();
        stubKeycloakCreateUser(keycloakUserId);
        stubKeycloakAssignRole(keycloakUserId);

        String body = """
                {
                    "email": "dup@example.com",
                    "password": "password123",
                    "nickname": "testuser",
                    "slackId": null
                }
                """;

        // when: 1차 가입 (성공)
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // when & then: 2차 가입 (같은 이메일 → 409)
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER-002"));
    }

    /**
     * [시나리오 3] 필수 필드 누락 (email 없음)
     *
     * 흐름: 요청 → Bean Validation 실패 → Keycloak/DB 호출 없이 즉시 거절
     *
     * 검증:
     * - 응답 400 Bad Request + errorCode = COMMON-001
     */
    @Test
    void signup_missingEmail_returns400() throws Exception {
        // given: email 필드가 빠진 요청
        String body = """
                {
                    "password": "password123",
                    "nickname": "testuser"
                }
                """;

        // when & then: validation 단계에서 바로 400 반환
        mockMvc.perform(post("/api/v1/users/signup")
                        .header("X-Gateway-Secret", "test-gateway-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    // -------------------------------------------------------------------------
    // WireMock stub 헬퍼
    // -------------------------------------------------------------------------

    // Keycloak admin token 발급 (모든 테스트에서 공통 사용)
    private void stubAdminToken() {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/master/protocol/openid-connect/token"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-admin-token\",\"expires_in\":300}")));
    }

    // Keycloak 유저 생성 → Location 헤더로 생성된 userId 반환
    private void stubKeycloakCreateUser(String keycloakUserId) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/admin/realms/omc/users"))
                .willReturn(WireMock.aResponse()
                        .withStatus(201)
                        .withHeader("Location",
                                "http://localhost:" + wireMock.port() + "/admin/realms/omc/users/" + keycloakUserId)));
    }

    // Keycloak 역할 조회 + 역할 할당 (USER 역할)
    private void stubKeycloakAssignRole(String keycloakUserId) {
        // 1단계: USER 역할 정보 조회
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/admin/realms/omc/roles/USER"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"role-id\",\"name\":\"USER\"}")));

        // 2단계: 해당 유저에게 역할 할당
        wireMock.stubFor(WireMock.post(WireMock.urlPathMatching(
                        "/admin/realms/omc/users/" + keycloakUserId + "/role-mappings/realm"))
                .willReturn(WireMock.aResponse().withStatus(204)));
    }
}
