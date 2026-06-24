package com.omc.raffle.presentation.controller.admin;

import com.omc.common.config.GatewaySecurityAutoConfiguration;
import com.omc.raffle.application.service.RaffleDrawService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRaffleController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import(GatewaySecurityAutoConfiguration.class)   // @WebMvcTest에서 명시적 로딩
@MockBean(JpaMetamodelMappingContext.class)        // @EnableJpaAuditing ↔ @WebMvcTest 충돌 방지
@DisplayName("AdminRaffleController 보안 테스트")
class AdminRaffleControllerTest {

    private static final String GATEWAY_SECRET = "omc-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaffleDrawService raffleDrawService;

    @Test
    @DisplayName("ADMIN 역할 + Gateway Secret → 200 OK")
    void drawRaffle_adminRole_success() throws Exception {
        UUID raffleId = UUID.randomUUID();
        doNothing().when(raffleDrawService).drawRaffle(any(UUID.class));

        mockMvc.perform(post("/api/v1/admin/raffles/{raffleId}/draw", raffleId)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("USER 역할 → 403 Forbidden (ADMIN 전용 API)")
    void drawRaffle_userRole_forbidden() throws Exception {
        UUID raffleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/raffles/{raffleId}/draw", raffleId)
                        .header("X-Gateway-Secret", GATEWAY_SECRET)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        verify(raffleDrawService, never()).drawRaffle(any());
    }

    @Test
    @DisplayName("Gateway Secret 없음 → 403 Forbidden (Gateway 우회 차단)")
    void drawRaffle_noGatewaySecret_forbidden() throws Exception {
        UUID raffleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/raffles/{raffleId}/draw", raffleId)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        verify(raffleDrawService, never()).drawRaffle(any());
    }

    @Test
    @DisplayName("인증 헤더 없음 → 403 Forbidden")
    void drawRaffle_noAuth_forbidden() throws Exception {
        UUID raffleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/raffles/{raffleId}/draw", raffleId))
                .andExpect(status().isForbidden());

        verify(raffleDrawService, never()).drawRaffle(any());
    }
}
