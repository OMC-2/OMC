package com.omc.notification.infrastructure.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SlackClient {

    public void sendMessage(String slackId, String text) {
        // TODO: Slack Bot Token 발급 후 실제 API 연동
        log.info("[SlackClient] 슬랙 메시지 전송했습니다. slackId={}, text={}", slackId, text);
    }
}
