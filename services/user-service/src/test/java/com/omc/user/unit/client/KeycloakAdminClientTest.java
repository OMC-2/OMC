package com.omc.user.unit.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.omc.common.exception.BusinessException;
import com.omc.user.infrastructure.client.KeycloakAdminClient;
import com.omc.user.infrastructure.config.KeycloakProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakAdminClientTest {

    static WireMockServer wireMock;
    KeycloakAdminClient client;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        KeycloakProperties props = new KeycloakProperties();
        props.setServerUrl("http://localhost:" + wireMock.port());
        props.setRealm("omc");
        props.setClientId("omc-client");
        props.setAdminUsername("admin");
        props.setAdminPassword("admin");

        client = new KeycloakAdminClient(props);
    }

    // =========================================================================
    // setDbUserId
    // =========================================================================

    @Test
    void setDbUserId_성공_시_GET_후_PUT_API_호출() {
        String keycloakUserId = UUID.randomUUID().toString();
        String nickname = "testuser";
        String dbUserId = UUID.randomUUID().toString();

        stubAdminToken();
        stubGetUser(keycloakUserId, nickname);
        wireMock.stubFor(put(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .willReturn(aResponse().withStatus(204)));

        client.setDbUserId(keycloakUserId, nickname, dbUserId);

        // GET → PUT 순서 확인
        wireMock.verify(getRequestedFor(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId)));
        wireMock.verify(putRequestedFor(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .withRequestBody(matchingJsonPath("$.attributes.db_user_id[0]", equalTo(dbUserId)))
                .withRequestBody(matchingJsonPath("$.attributes.nickname[0]", equalTo(nickname))));
    }

    @Test
    void setDbUserId_실패_시_BusinessException_발생() {
        String keycloakUserId = UUID.randomUUID().toString();

        stubAdminToken();
        stubGetUser(keycloakUserId, "nickname");
        wireMock.stubFor(put(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.setDbUserId(keycloakUserId, "nickname", UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void setDbUserId_기존_user_표현_보존하여_PUT() {
        String keycloakUserId = UUID.randomUUID().toString();
        String nickname = "mynameis";
        String dbUserId = UUID.randomUUID().toString();

        stubAdminToken();
        stubGetUser(keycloakUserId, nickname);
        wireMock.stubFor(put(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .willReturn(aResponse().withStatus(204)));

        client.setDbUserId(keycloakUserId, nickname, dbUserId);

        // PUT body에 email 필드 보존 확인 (GET 응답에 포함된 필드)
        wireMock.verify(putRequestedFor(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .withRequestBody(matchingJsonPath("$.email", equalTo(nickname + "@example.com"))));

        // db_user_id와 nickname attribute 포함 확인
        wireMock.verify(putRequestedFor(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .withRequestBody(matchingJsonPath("$.attributes.db_user_id[0]", equalTo(dbUserId)))
                .withRequestBody(matchingJsonPath("$.attributes.nickname[0]", equalTo(nickname))));
    }

    // =========================================================================
    // createUser
    // =========================================================================

    @Test
    void createUser_성공_시_keycloakUserId_반환() {
        String expectedKeycloakId = UUID.randomUUID().toString();

        stubAdminToken();
        wireMock.stubFor(post(urlEqualTo("/admin/realms/omc/users"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Location", "http://localhost:" + wireMock.port()
                                + "/admin/realms/omc/users/" + expectedKeycloakId)));
        wireMock.stubFor(get(urlEqualTo("/admin/realms/omc/roles/USER"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"role-id\",\"name\":\"USER\"}")));
        wireMock.stubFor(post(urlEqualTo("/admin/realms/omc/users/" + expectedKeycloakId + "/role-mappings/realm"))
                .willReturn(aResponse().withStatus(204)));

        String result = client.createUser("test@example.com", "password123", "testuser");

        assertThat(result).isEqualTo(expectedKeycloakId);
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private void stubAdminToken() {
        wireMock.stubFor(post(urlEqualTo("/realms/master/protocol/openid-connect/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"mock-admin-token\",\"expires_in\":300}")));
    }

    private void stubGetUser(String keycloakUserId, String nickname) {
        wireMock.stubFor(get(urlEqualTo("/admin/realms/omc/users/" + keycloakUserId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{" +
                                "\"id\":\"" + keycloakUserId + "\"," +
                                "\"username\":\"" + nickname + "@example.com\"," +
                                "\"email\":\"" + nickname + "@example.com\"," +
                                "\"emailVerified\":true," +
                                "\"enabled\":true," +
                                "\"attributes\":{\"nickname\":[\"" + nickname + "\"]}" +
                                "}")));
    }
}
