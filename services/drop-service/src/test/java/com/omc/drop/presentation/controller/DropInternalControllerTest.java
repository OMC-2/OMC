package com.omc.drop.presentation.controller;

import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.drop.application.service.DropQueryService;
import com.omc.drop.infrastructure.config.SecurityConfig;
import com.omc.drop.presentation.dto.response.ActiveDropResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DropInternalController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "gateway.secret=test-secret")
@DisplayName("DropInternalController 테스트")
class DropInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DropQueryService dropQueryService;

    private static final String GATEWAY_SECRET = "test-secret";

    @Nested
    @DisplayName("GET /internal/v1/drops/products/{productId}/active")
    class HasActiveDrop {

        @Test
        @DisplayName("활성 드롭이 있으면 hasActiveDrop=true를 반환한다")
        void returnsTrueWhenActiveDropExists() throws Exception {
            UUID productId = UUID.randomUUID();
            when(dropQueryService.hasActiveDrop(productId)).thenReturn(ActiveDropResponse.of(true));

            mockMvc.perform(get("/internal/v1/drops/products/{productId}/active", productId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasActiveDrop").value(true));
        }

        @Test
        @DisplayName("활성 드롭이 없으면 hasActiveDrop=false를 반환한다")
        void returnsFalseWhenNoActiveDrop() throws Exception {
            UUID productId = UUID.randomUUID();
            when(dropQueryService.hasActiveDrop(productId)).thenReturn(ActiveDropResponse.of(false));

            mockMvc.perform(get("/internal/v1/drops/products/{productId}/active", productId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasActiveDrop").value(false));
        }

        @Test
        @DisplayName("사용자 인증 헤더 없이 게이트웨이 시크릿만으로 200을 반환한다")
        void returns200WithoutUserAuthHeaders() throws Exception {
            UUID productId = UUID.randomUUID();
            when(dropQueryService.hasActiveDrop(productId)).thenReturn(ActiveDropResponse.of(false));

            // /internal/** 은 permitAll() — X-User-Id·X-User-Role 없어도 통과
            mockMvc.perform(get("/internal/v1/drops/products/{productId}/active", productId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET))
                    .andExpect(status().isOk());
        }
    }
}
