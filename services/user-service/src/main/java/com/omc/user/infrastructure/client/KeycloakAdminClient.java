package com.omc.user.infrastructure.client;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.user.domain.exception.UserErrorCode;
import com.omc.user.infrastructure.config.KeycloakProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final KeycloakProperties props;
    private final RestClient restClient = RestClient.create();

    public String createUser(String email, String password, String nickname) {
        return createKeycloakUser(email, password, nickname, "USER");
    }

    public String createAdminUser(String email, String password, String nickname) {
        return createKeycloakUser(email, password, nickname, "ADMIN");
    }

    private String createKeycloakUser(String email, String password, String nickname, String roleName) {
        String adminToken = getAdminToken();

        Map<String, Object> body = Map.of(
                "username", email,
                "email", email,
                "emailVerified", true,
                "firstName", nickname,
                "lastName", "-",
                "enabled", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false
                )),
                "attributes", Map.of("nickname", List.of(nickname))
        );

        try {
            var response = restClient.post()
                    .uri(adminUrl("/users"))
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            String location = response.getHeaders().getFirst("Location");
            if (location == null) {
                throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
            }
            String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
            assignRole(adminToken, keycloakUserId, roleName);
            return keycloakUserId;

        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 409) {
                throw new BusinessException(UserErrorCode.USER_ALREADY_EXISTS);
            }
            log.error("Keycloak user creation failed: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED);
        } catch (Exception e) {
            log.error("Keycloak user creation failed", e);
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED);
        }
    }

    public void deleteUser(String keycloakUserId) {
        try {
            String adminToken = getAdminToken();
            restClient.delete()
                    .uri(adminUrl("/users/" + keycloakUserId))
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Keycloak rollback failed for user {}", keycloakUserId, e);
        }
    }

    @SuppressWarnings("unchecked")
    public KeycloakTokenResponse login(String email, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", props.getClientId());
        form.add("username", email);
        form.add("password", password);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(props.getServerUrl() + "/realms/" + props.getRealm() + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            return new KeycloakTokenResponse(
                    (String) response.get("access_token"),
                    (String) response.get("refresh_token"),
                    ((Number) response.get("expires_in")).longValue()
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(UserErrorCode.INVALID_PASSWORD);
            }
            log.error("Keycloak login failed: {}", e.getMessage());
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED);
        } catch (Exception e) {
            log.error("Keycloak login failed", e);
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private String getAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", props.getAdminUsername());
        form.add("password", props.getAdminPassword());

        try {
            Map<String, Object> response = restClient.post()
                    .uri(props.getServerUrl() + "/realms/master/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            return (String) response.get("access_token");
        } catch (Exception e) {
            log.error("Failed to get Keycloak admin token", e);
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private void assignRole(String adminToken, String keycloakUserId, String roleName) {
        try {
            Map<String, Object> role = restClient.get()
                    .uri(adminUrl("/roles/" + roleName))
                    .header("Authorization", "Bearer " + adminToken)
                    .retrieve()
                    .body(Map.class);

            restClient.post()
                    .uri(adminUrl("/users/" + keycloakUserId + "/role-mappings/realm"))
                    .header("Authorization", "Bearer " + adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to assign {} role to {} in Keycloak", roleName, keycloakUserId, e);
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED);
        }
    }

    private String adminUrl(String path) {
        return props.getServerUrl() + "/admin/realms/" + props.getRealm() + path;
    }
}
