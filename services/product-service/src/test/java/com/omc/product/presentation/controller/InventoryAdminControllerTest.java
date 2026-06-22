package com.omc.product.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.product.application.service.InventoryService;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.infrastructure.config.SecurityConfig;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import com.omc.product.presentation.dto.response.InventoryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class InventoryAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean
    InventoryService inventoryService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String GW_SECRET = "test-gateway-secret";

    private InventoryResponse inventoryResponse() {
        return new InventoryResponse(
                UUID.randomUUID(), PRODUCT_ID,
                10, 3, 7, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("재고 상세 조회 API")
    class GetInventoryApi {

        @Test
        @DisplayName("ADMIN이면 200을 반환한다")
        void getInventory_success() throws Exception {
            given(inventoryService.getInventory(PRODUCT_ID)).willReturn(inventoryResponse());

            mockMvc.perform(get("/api/v1/admin/products/{productId}/inventories", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalQuantity").value(10))
                    .andExpect(jsonPath("$.data.availableQuantity").value(7));
        }

        @Test
        @DisplayName("USER 권한이면 403을 반환한다")
        void getInventory_forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/admin/products/{productId}/inventories", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("존재하지 않는 재고이면 404를 반환한다")
        void getInventory_notFound() throws Exception {
            given(inventoryService.getInventory(PRODUCT_ID))
                    .willThrow(new InventoryNotFoundException());

            mockMvc.perform(get("/api/v1/admin/products/{productId}/inventories", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("재고 수동 수정 API")
    class UpdateInventoryApi {

        @Test
        @DisplayName("ADMIN이면 200을 반환한다")
        void updateInventory_success() throws Exception {
            InventoryUpdateRequest request = new InventoryUpdateRequest(15, "입고 추가 5개");
            InventoryResponse response = new InventoryResponse(
                    UUID.randomUUID(), PRODUCT_ID, 15, 3, 12, LocalDateTime.now()
            );
            given(inventoryService.updateInventory(any(), any())).willReturn(response);

            mockMvc.perform(patch("/api/v1/admin/products/{productId}/inventories", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalQuantity").value(15))
                    .andExpect(jsonPath("$.data.availableQuantity").value(12));
        }

        @Test
        @DisplayName("reason이 없으면 400을 반환한다")
        void updateInventory_missingReason() throws Exception {
            String invalidRequest = """
                { "totalQuantity": 15 }
                """;

            mockMvc.perform(patch("/api/v1/admin/products/{productId}/inventories", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("재고 수정 시 진행 중인 Drop이 있으면 409를 반환한다")
        void updateInventory_activeDropExists() throws Exception {
            InventoryUpdateRequest request = new InventoryUpdateRequest(15, "입고 추가");
            given(inventoryService.updateInventory(any(), any()))
                    .willThrow(new ActiveDropExistsException());

            mockMvc.perform(patch("/api/v1/admin/products/{productId}/inventories", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }
}