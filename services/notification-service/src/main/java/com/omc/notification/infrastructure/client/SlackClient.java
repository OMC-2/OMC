package com.omc.notification.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackClient {

    private final RestClient restClient;

    @Value("${slack.bot-token:}")
    private String botToken;

    public SlackClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://slack.com").build();
    }

    public void sendMessage(String slackId, String text) {
        if (slackId == null || botToken.isBlank()) {
            log.warn("[SlackClient] 전송 스킵. slackId={}, tokenPresent={}", slackId, !botToken.isBlank());
            return;
        }

        Map<?, ?> response = restClient.post()
                .uri("/api/chat.postMessage")
                .header("Authorization", "Bearer " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", slackId, "text", text))
                .retrieve()
                .body(Map.class);

        if (response != null && Boolean.TRUE.equals(response.get("ok"))) {
            log.info("[SlackClient] 전송 성공. slackId={}", slackId);
        } else {
            log.error("[SlackClient] 전송 실패. slackId={}, error={}", slackId,
                    response != null ? response.get("error") : "null response");
        }
    }
}
