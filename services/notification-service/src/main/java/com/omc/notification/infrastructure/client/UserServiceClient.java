package com.omc.notification.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", url = "${feign.client.user-service.url:http://user-service}")
public interface UserServiceClient {

    @GetMapping("/internal/v1/users/{userId}/slack")
    SlackApiResponse getSlackId(@PathVariable UUID userId);

    @PostMapping("/internal/v1/users/slack/batch")
    SlackBatchApiResponse getSlackIdsBatch(@RequestBody List<UUID> userIds);

    record UserSlackResponse(UUID userId, String slackId) {}

    record SlackApiResponse(boolean success, int status, String message, UserSlackResponse data) {}

    record SlackBatchApiResponse(boolean success, int status, String message, List<UserSlackResponse> data) {}
}
