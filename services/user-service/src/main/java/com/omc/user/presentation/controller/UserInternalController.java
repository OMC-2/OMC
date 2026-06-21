package com.omc.user.presentation.controller;

import com.omc.common.response.ApiResponse;
import com.omc.user.application.service.UserService;
import com.omc.user.presentation.dto.response.UserIdResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
