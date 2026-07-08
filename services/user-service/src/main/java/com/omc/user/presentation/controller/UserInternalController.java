package com.omc.user.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.user.application.service.UserService;
import com.omc.user.presentation.dto.response.UserIdResponse;
import com.omc.user.presentation.dto.response.UserSlackResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class UserInternalController {

    private final UserService userService;

    @GetMapping("/keycloak/{keycloakId}")
    public ResponseEntity<ApiResponse<UserIdResponse>> getUserIdByKeycloakId(
            @PathVariable String keycloakId) {
        return ResponseEntity.ok(ApiResponse.success(userService.findUserIdByKeycloakId(keycloakId)));
    }

    @GetMapping("/{userId}/slack")
    public ResponseEntity<ApiResponse<UserSlackResponse>> getSlackId(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.findSlackIdByUserId(userId)));
    }

    @PostMapping("/slack/batch")
    public ResponseEntity<ApiResponse<List<UserSlackResponse>>> getSlackIdsBatch(
            @RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(ApiResponse.success(userService.findSlackIdsByUserIds(userIds)));
    }
}
