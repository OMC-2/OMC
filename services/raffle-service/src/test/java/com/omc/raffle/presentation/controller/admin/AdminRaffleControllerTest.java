package com.omc.raffle.presentation.controller.admin;

import com.omc.raffle.application.service.RaffleDrawService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRaffleController.class)
@AutoConfigureMockMvc(addFilters = false) // 공통 시큐리티 필터 우회 (Controller 순수 로직만 검증)
class AdminRaffleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaffleDrawService raffleDrawService;

    @Test
    @DisplayName("수동 추첨 실행 시 200 OK를 반환한다")
    void drawRaffleSuccess() throws Exception {
        UUID raffleId = UUID.randomUUID();

        doNothing().when(raffleDrawService).drawRaffle(any(UUID.class));

        mockMvc.perform(post("/api/v1/admin/raffles/{raffleId}/draw", raffleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("OK"));
    }
}
