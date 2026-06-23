package com.omc.product.presentation.controller;

import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.product.application.service.InventoryService;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.infrastructure.config.SecurityConfig;
import com.omc.product.presentation.dto.response.InventorySnapshotResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryInternalController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class InventoryInternalControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean InventoryService inventoryService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String GW_SECRET = "test-gateway-secret";

    @Nested
    @DisplayName("재고 스냅샷 조회 API")
    class GetSnapshotApi {

        @Test
        @DisplayName("정상 요청이면 200을 반환한다")
        void getSnapshot_success() throws Exception {
            InventorySnapshotResponse response = new InventorySnapshotResponse(
                    PRODUCT_ID, 10, 3, 7
            );
            given(inventoryService.getSnapshot(PRODUCT_ID)).willReturn(response);

            mockMvc.perform(get("/internal/v1/products/{productId}/inventories/snapshot", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalQuantity").value(10))
                    .andExpect(jsonPath("$.data.soldQuantity").value(3))
                    .andExpect(jsonPath("$.data.availableQuantity").value(7));
        }

        @Test
        @DisplayName("존재하지 않는 재고이면 404를 반환한다")
        void getSnapshot_notFound() throws Exception {
            given(inventoryService.getSnapshot(PRODUCT_ID))
                    .willThrow(new InventoryNotFoundException());

            mockMvc.perform(get("/internal/v1/products/{productId}/inventories/snapshot", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isNotFound());
        }
    }
}