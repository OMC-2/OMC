package com.omc.drop.presentation.controller;

import com.omc.drop.application.service.DropQueryService;
import com.omc.drop.application.service.PurchaseService;
import com.omc.drop.domain.exception.DuplicatePurchaseException;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.SoldOutException;
import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.drop.infrastructure.config.SecurityConfig;
import com.omc.drop.presentation.dto.response.PurchaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DropController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "gateway.secret=test-secret")
@DisplayName("DropController 테스트")
class DropControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PurchaseService purchaseService;

    @MockBean
    private DropQueryService dropQueryService;

    private static final String GATEWAY_SECRET = "test-secret";
    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("POST /api/v1/drops/{dropId}/purchase")
    class Purchase {

        @Test
        @DisplayName("선점 성공 시 202와 orderId·queueNumber를 반환한다")
        void returns202WithOrderIdAndQueueNumber() throws Exception {
            UUID dropId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();

            when(purchaseService.purchase(dropId, userId))
                    .thenReturn(new PurchaseResponse(orderId, 42L));

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.data.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.data.queueNumber").value(42));
        }

        @Test
        @DisplayName("게이트웨이 시크릿이 없으면 403을 반환한다")
        void returns403WhenNoGatewaySecret() throws Exception {
            UUID dropId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("DROP_NOT_OPEN이면 409와 에러코드 DROP-002를 반환한다")
        void returns409WhenDropNotOpen() throws Exception {
            UUID dropId = UUID.randomUUID();

            when(purchaseService.purchase(dropId, userId))
                    .thenThrow(new DropNotOpenException());

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DROP-002"));
        }

        @Test
        @DisplayName("SOLD_OUT이면 409와 에러코드 DROP-004를 반환한다")
        void returns409WhenSoldOut() throws Exception {
            UUID dropId = UUID.randomUUID();

            when(purchaseService.purchase(dropId, userId))
                    .thenThrow(new SoldOutException());

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DROP-004"));
        }

        @Test
        @DisplayName("DUPLICATE_PURCHASE이면 409와 에러코드 DROP-005를 반환한다")
        void returns409WhenDuplicatePurchase() throws Exception {
            UUID dropId = UUID.randomUUID();

            when(purchaseService.purchase(dropId, userId))
                    .thenThrow(new DuplicatePurchaseException());

            mockMvc.perform(post("/api/v1/drops/{dropId}/purchase", dropId)
                            .header("X-Gateway-Secret", GATEWAY_SECRET)
                            .header("X-User-Id", userId.toString())
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DROP-005"));
        }
    }
}
