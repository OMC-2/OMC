package com.omc.user.presentation.controller;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.ApiResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.user.application.service.UserService;
import com.omc.user.presentation.dto.request.LoginRequest;
import com.omc.user.presentation.dto.request.SignupRequest;
import com.omc.user.presentation.dto.response.LoginResponse;
import com.omc.user.presentation.dto.response.SignupResponse;
import com.omc.user.presentation.dto.response.UserProfileResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Value("${admin.secret}")
    private String adminSecret;

    @GetMapping("/health")
    public String health() {
        return "user-service is running";
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userService.signup(request)));
    }

    @PostMapping("/admin/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> adminSignup(
            @RequestHeader("X-Admin-Secret") String secret,
            @Valid @RequestBody SignupRequest request) {
        if (!adminSecret.equals(secret)) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(userService.adminSignup(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.login(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        UUID userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(userId)));
    }

    // =====================================================================
    // 테스트 전용 엔드포인트 — 인가 흐름 검증용, 운영 사용 금지
    // Gateway가 주입한 X-User-Id / X-User-Role 헤더가 SecurityContext까지
    // 올바르게 전달되는지 확인하고 @PreAuthorize 동작을 검증한다.
    // =====================================================================

    /**
     * [TEST] ADMIN 전용 접근 테스트
     * - ADMIN 토큰: 200 + userId/role 반환
     * - USER 토큰:  403
     * - 토큰 없음:  Gateway에서 401 차단
     */
    @GetMapping("/test/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> testAdminOnly() {
        String userId = SecurityUtil.getCurrentUserId()
                .map(UUID::toString)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        String role = SecurityUtil.getCurrentUserRole()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(Map.of("userId", userId, "role", role)));
    }

    /**
     * [TEST] USER 전용 접근 테스트
     * - USER 토큰:  200 + userId/role 반환
     * - ADMIN 토큰: 403
     * - 토큰 없음:  Gateway에서 401 차단
     */
    @GetMapping("/test/user-only")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> testUserOnly() {
        String userId = SecurityUtil.getCurrentUserId()
                .map(UUID::toString)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        String role = SecurityUtil.getCurrentUserRole()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
        return ResponseEntity.ok(ApiResponse.success(Map.of("userId", userId, "role", role)));
    }
}
