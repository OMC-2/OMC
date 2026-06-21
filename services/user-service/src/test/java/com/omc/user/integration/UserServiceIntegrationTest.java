package com.omc.user.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omc.user.domain.entity.Address;
import com.omc.user.domain.entity.User;
import com.omc.user.domain.repository.AddressRepository;
import com.omc.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * user-service 전체 통합 테스트.
 * PostgreSQL 컨테이너와 Spring ApplicationContext를 1번만 생성하고
 * @Nested 클래스로 기능별 테스트를 분리합니다.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class UserServiceIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    static final WireMockServer wireMock = new WireMockServer(
            WireMockConfiguration.options().dynamicPort());

    static {
        postgres.start();
        wireMock.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("keycloak.server-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AddressRepository addressRepository;

    // =========================================================================
    // 회원가입
    // =========================================================================

    @Nested
    class SignupTests {

        @BeforeEach
        void setUp() {
            wireMock.resetAll();
            addressRepository.deleteAll();
            userRepository.deleteAll();
            stubAdminToken();
        }

        @Test
        void signup_success_returns201() throws Exception {
            String keycloakUserId = UUID.randomUUID().toString();
            stubKeycloakCreateUser(keycloakUserId);
            stubKeycloakAssignRole(keycloakUserId);

            mockMvc.perform(post("/api/v1/users/signup")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "email": "test@example.com",
                                        "password": "password123",
                                        "nickname": "testuser",
                                        "slackId": "U12345"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"))
                    .andExpect(jsonPath("$.data.role").value("USER"))
                    .andExpect(jsonPath("$.data.userId").isNotEmpty());

            assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        }

        @Test
        void signup_duplicateEmail_returns409() throws Exception {
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

            mockMvc.perform(post("/api/v1/users/signup")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/users/signup")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("USER-002"));
        }

        @Test
        void signup_missingEmail_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/users/signup")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "password": "password123",
                                        "nickname": "testuser"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
        }

        private void stubAdminToken() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/master/protocol/openid-connect/token"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"access_token\":\"mock-admin-token\",\"expires_in\":300}")));
        }

        private void stubKeycloakCreateUser(String keycloakUserId) {
            wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/admin/realms/omc/users"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(201)
                            .withHeader("Location",
                                    "http://localhost:" + wireMock.port() + "/admin/realms/omc/users/" + keycloakUserId)));
        }

        private void stubKeycloakAssignRole(String keycloakUserId) {
            wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/admin/realms/omc/roles/USER"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"id\":\"role-id\",\"name\":\"USER\"}")));

            wireMock.stubFor(WireMock.post(WireMock.urlPathMatching(
                            "/admin/realms/omc/users/" + keycloakUserId + "/role-mappings/realm"))
                    .willReturn(WireMock.aResponse().withStatus(204)));
        }
    }

    // =========================================================================
    // 로그인 / 토큰 갱신
    // =========================================================================

    @Nested
    class AuthTests {

        @BeforeEach
        void setUp() {
            wireMock.resetAll();
            addressRepository.deleteAll();
            userRepository.deleteAll();
        }

        @Test
        void login_success_returns200() throws Exception {
            stubKeycloakLogin();

            mockMvc.perform(post("/api/v1/users/login")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "email": "test@example.com",
                                        "password": "password123"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("mock-refresh-token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(3600));
        }

        @Test
        void login_keycloakUnauthorized_returns401() throws Exception {
            stubKeycloakLoginUnauthorized();

            mockMvc.perform(post("/api/v1/users/login")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "email": "test@example.com",
                                        "password": "wrongpassword"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("USER-004"));
        }

        @Test
        void login_missingEmail_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/users/login")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "password": "password123"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
        }

        @Test
        void refresh_success_returns200() throws Exception {
            stubKeycloakRefresh();

            mockMvc.perform(post("/api/v1/users/token/refresh")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "valid-refresh-token"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        }

        @Test
        void refresh_expiredToken_returns401() throws Exception {
            stubKeycloakRefreshUnauthorized();

            mockMvc.perform(post("/api/v1/users/token/refresh")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "refreshToken": "expired-refresh-token"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("COMMON-007"));
        }

        @Test
        void refresh_missingRefreshToken_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/users/token/refresh")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
        }

        private void stubKeycloakLogin() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/omc/protocol/openid-connect/token"))
                    .withRequestBody(WireMock.containing("grant_type=password"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                        "access_token": "mock-access-token",
                                        "refresh_token": "mock-refresh-token",
                                        "expires_in": 3600
                                    }
                                    """)));
        }

        private void stubKeycloakLoginUnauthorized() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/omc/protocol/openid-connect/token"))
                    .withRequestBody(WireMock.containing("grant_type=password"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(401)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"invalid_grant\"}")));
        }

        private void stubKeycloakRefresh() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/omc/protocol/openid-connect/token"))
                    .withRequestBody(WireMock.containing("grant_type=refresh_token"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                        "access_token": "new-access-token",
                                        "refresh_token": "new-refresh-token",
                                        "expires_in": 3600
                                    }
                                    """)));
        }

        private void stubKeycloakRefreshUnauthorized() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/omc/protocol/openid-connect/token"))
                    .withRequestBody(WireMock.containing("grant_type=refresh_token"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(401)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"invalid_token\"}")));
        }
    }

    // =========================================================================
    // 사용자 프로필 (GET/PATCH/DELETE /api/v1/users/me)
    // =========================================================================

    @Nested
    class ProfileTests {

        private static final String KEYCLOAK_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        private User savedUser;

        @BeforeEach
        void setUp() {
            wireMock.resetAll();
            stubAdminToken();
            addressRepository.deleteAll();
            userRepository.deleteAll();
            savedUser = userRepository.save(
                    User.create(KEYCLOAK_ID, "test@example.com", "testuser", "U12345"));
        }

        @Test
        void getProfile_authenticated_returns200() throws Exception {
            mockMvc.perform(get("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("testuser"))
                    .andExpect(jsonPath("$.data.slackId").value("U12345"))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        void getProfile_noUserIdHeader_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void updateProfile_nickname_returns200() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "nickname": "newnickname"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("newnickname"));

            User updated = userRepository.findByKeycloakId(KEYCLOAK_ID).orElseThrow();
            assertThat(updated.getNickname()).isEqualTo("newnickname");
        }

        @Test
        void updateProfile_slackId_returns200() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "slackId": "U99999"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.slackId").value("U99999"));

            User updated = userRepository.findByKeycloakId(KEYCLOAK_ID).orElseThrow();
            assertThat(updated.getSlackId()).isEqualTo("U99999");
            assertThat(updated.getNickname()).isEqualTo("testuser");
        }

        @Test
        void updateProfile_noUserIdHeader_returns403() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "nickname": "newnickname"
                                    }
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        void withdraw_success_returns200() throws Exception {
            stubKeycloakDeleteUser(savedUser.getKeycloakId());

            mockMvc.perform(delete("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(userRepository.existsByEmail("test@example.com")).isFalse();
        }

        @Test
        void withdraw_keycloakFails_dbDeleteProceeds() throws Exception {
            stubKeycloakDeleteUserFails(savedUser.getKeycloakId());

            mockMvc.perform(delete("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(userRepository.existsByEmail("test@example.com")).isFalse();
        }

        @Test
        void withdraw_noUserIdHeader_returns403() throws Exception {
            mockMvc.perform(delete("/api/v1/users/me")
                            .header("X-Gateway-Secret", "test-gateway-secret"))
                    .andExpect(status().isForbidden());
        }

        private void stubAdminToken() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/master/protocol/openid-connect/token"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"access_token\":\"mock-admin-token\",\"expires_in\":300}")));
        }

        private void stubKeycloakDeleteUser(String keycloakUserId) {
            wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                    .willReturn(WireMock.aResponse().withStatus(204)));
        }

        private void stubKeycloakDeleteUserFails(String keycloakUserId) {
            wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                    .willReturn(WireMock.aResponse()
                            .withStatus(500)
                            .withBody("{\"error\":\"internal_server_error\"}")));
        }
    }

    // =========================================================================
    // 배송지 CRUD
    // =========================================================================

    @Nested
    class AddressTests {

        private static final String KEYCLOAK_ID       = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
        private static final String OTHER_KEYCLOAK_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        private User savedUser;

        @BeforeEach
        void setUp() {
            wireMock.resetAll();
            stubAdminToken();
            addressRepository.deleteAll();
            userRepository.deleteAll();
            savedUser = userRepository.save(
                    User.create(KEYCLOAK_ID, "test@example.com", "testuser", null));
        }

        @Test
        void createAddress_nonDefault_returns201() throws Exception {
            mockMvc.perform(post("/api/v1/users/addresses")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "recipientName": "홍길동",
                                        "phone": "010-1234-5678",
                                        "zipCode": "12345",
                                        "address": "서울시 강남구",
                                        "isDefault": false
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.addressId").isNotEmpty())
                    .andExpect(jsonPath("$.data.recipientName").value("홍길동"))
                    .andExpect(jsonPath("$.data.isDefault").value(false));

            assertThat(addressRepository.findAllByUserId(savedUser.getUserId())).hasSize(1);
        }

        @Test
        void createAddress_asDefault_unmarksExisting() throws Exception {
            Address existing = addressRepository.save(Address.create(
                    savedUser.getUserId(), "기존수신자", "010-0000-0000", "00000", "기존주소", null, true));

            mockMvc.perform(post("/api/v1/users/addresses")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "recipientName": "새수신자",
                                        "phone": "010-9999-9999",
                                        "zipCode": "99999",
                                        "address": "새주소",
                                        "isDefault": true
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            Address refreshed = addressRepository.findById(existing.getAddressId()).orElseThrow();
            assertThat(refreshed.isDefault()).isFalse();
        }

        @Test
        void createAddress_missingRequiredField_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/users/addresses")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "phone": "010-1234-5678",
                                        "zipCode": "12345",
                                        "address": "서울시 강남구"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
        }

        @Test
        void createAddress_noUserIdHeader_returns403() throws Exception {
            mockMvc.perform(post("/api/v1/users/addresses")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "recipientName": "홍길동",
                                        "phone": "010-1234-5678",
                                        "zipCode": "12345",
                                        "address": "서울시 강남구"
                                    }
                                    """))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getAddresses_returns200WithPageResponse() throws Exception {
            addressRepository.save(Address.create(
                    savedUser.getUserId(), "홍길동", "010-1111-1111", "11111", "주소1", null, true));
            addressRepository.save(Address.create(
                    savedUser.getUserId(), "김철수", "010-2222-2222", "22222", "주소2", null, false));

            mockMvc.perform(get("/api/v1/users/addresses")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        void getAddresses_noUserIdHeader_returns403() throws Exception {
            mockMvc.perform(get("/api/v1/users/addresses")
                            .header("X-Gateway-Secret", "test-gateway-secret"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getAddress_success_returns200() throws Exception {
            Address saved = addressRepository.save(Address.create(
                    savedUser.getUserId(), "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호", false));

            mockMvc.perform(get("/api/v1/users/addresses/" + saved.getAddressId())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.addressId").value(saved.getAddressId().toString()))
                    .andExpect(jsonPath("$.data.recipientName").value("홍길동"))
                    .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.zipCode").value("12345"))
                    .andExpect(jsonPath("$.data.address").value("서울시 강남구"))
                    .andExpect(jsonPath("$.data.addressDetail").value("101호"));
        }

        @Test
        void getAddress_notFound_returns404() throws Exception {
            mockMvc.perform(get("/api/v1/users/addresses/" + UUID.randomUUID())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("USER-101"));
        }

        @Test
        void getAddress_otherUsersAddress_returns404() throws Exception {
            User otherUser = userRepository.save(
                    User.create(OTHER_KEYCLOAK_ID, "other@example.com", "other", null));
            Address otherAddress = addressRepository.save(Address.create(
                    otherUser.getUserId(), "다른사람", "010-9999-9999", "99999", "다른주소", null, false));

            mockMvc.perform(get("/api/v1/users/addresses/" + otherAddress.getAddressId())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("USER-101"));
        }

        @Test
        void updateAddress_partialUpdate_returns200() throws Exception {
            Address saved = addressRepository.save(Address.create(
                    savedUser.getUserId(), "원래이름", "010-1234-5678", "12345", "원래주소", null, false));

            mockMvc.perform(patch("/api/v1/users/addresses/" + saved.getAddressId())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "recipientName": "수정된이름"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.recipientName").value("수정된이름"))
                    .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.address").value("원래주소"));
        }

        @Test
        void updateAddress_notFound_returns404() throws Exception {
            mockMvc.perform(patch("/api/v1/users/addresses/" + UUID.randomUUID())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "recipientName": "수정된이름"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("USER-101"));
        }

        @Test
        void deleteAddress_success_returns200() throws Exception {
            Address saved = addressRepository.save(Address.create(
                    savedUser.getUserId(), "홍길동", "010-1234-5678", "12345", "서울시 강남구", null, false));

            mockMvc.perform(delete("/api/v1/users/addresses/" + saved.getAddressId())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            assertThat(addressRepository.findByAddressIdAndUserId(
                    saved.getAddressId(), savedUser.getUserId())).isEmpty();
        }

        @Test
        void deleteAddress_notFound_returns404() throws Exception {
            mockMvc.perform(delete("/api/v1/users/addresses/" + UUID.randomUUID())
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("USER-101"));
        }

        @Test
        void setDefaultAddress_success_returns200() throws Exception {
            Address addr1 = addressRepository.save(Address.create(
                    savedUser.getUserId(), "기존default", "010-1111-1111", "11111", "주소1", null, true));
            Address addr2 = addressRepository.save(Address.create(
                    savedUser.getUserId(), "새default", "010-2222-2222", "22222", "주소2", null, false));

            mockMvc.perform(patch("/api/v1/users/addresses/" + addr2.getAddressId() + "/default")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.addressId").value(addr2.getAddressId().toString()))
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            assertThat(addressRepository.findById(addr1.getAddressId()).orElseThrow().isDefault()).isFalse();
            assertThat(addressRepository.findById(addr2.getAddressId()).orElseThrow().isDefault()).isTrue();
        }

        @Test
        void setDefaultAddress_notFound_returns404() throws Exception {
            mockMvc.perform(patch("/api/v1/users/addresses/" + UUID.randomUUID() + "/default")
                            .header("X-Gateway-Secret", "test-gateway-secret")
                            .header("X-User-Id", savedUser.getUserId().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("USER-101"));
        }

        private void stubAdminToken() {
            wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/realms/master/protocol/openid-connect/token"))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"access_token\":\"mock-admin-token\",\"expires_in\":300}")));
        }
    }
}
