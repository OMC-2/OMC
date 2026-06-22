package com.omc.notification.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", url = "${feign.client.user-service.url:http://user-service}")
public interface UserServiceClient {

    @GetMapping("/internal/v1/users/{userId}/slack")
    UserSlackResponse getSlackId(@PathVariable UUID userId);

    record UserSlackResponse(UUID userId, String slackId) {}
}
