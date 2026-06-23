package com.omc.raffle.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.raffle.application.dto.request.RaffleApplyRequest;
import com.omc.raffle.application.dto.response.RaffleApplyResponse;
import com.omc.raffle.application.service.RaffleAppService;
import com.omc.raffle.application.service.RaffleResultService;
import com.omc.raffle.domain.enums.RaffleResultStatus;
import com.omc.raffle.domain.enums.RaffleStatus;
import com.omc.raffle.presentation.dto.request.RaffleEnterRequest;
import com.omc.raffle.presentation.dto.response.RaffleResultResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RaffleController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter 우회
@MockBean(JpaMetamodelMappingContext.class) // @EnableJpaAuditing ↔ @WebMvcTest 충돌 방지
@DisplayName("RaffleController 슬라이스 테스트")
class RaffleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RaffleAppService raffleAppService;

    @MockBean
    private RaffleResultService raffleResultService;

    @Nested
    @DisplayName("래플 응모 API")
    class EnterRaffle {

        @Test
        @DisplayName("유효한 요청일 경우 201 Created를 반환한다")
        void success() throws Exception {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String billingKeyId = "bk_" + UUID.randomUUID().toString();
            RaffleEnterRequest request = new RaffleEnterRequest(
                    billingKeyId,
                    null,
                    BigDecimal.valueOf(10000),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(10000)
            );

            RaffleApplyResponse responseDto = new RaffleApplyResponse(
                    UUID.randomUUID(), raffleId, userId, billingKeyId, null,
                    BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000), LocalDateTime.now()
            );

            when(raffleAppService.apply(eq(raffleId), any(RaffleApplyRequest.class)))
                    .thenReturn(responseDto);

            // when & then
            mockMvc.perform(post("/api/v1/raffles/{raffleId}/entries", raffleId)
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.raffleId").value(raffleId.toString()))
                    .andExpect(jsonPath("$.data.userId").value(userId.toString()));
        }

        @Test
        @DisplayName("필수 파라미터 누락 시 400 Bad Request를 반환한다")
        void validationFail() throws Exception {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            // billingKeyId가 null인 잘못된 요청
            RaffleEnterRequest request = new RaffleEnterRequest(
                    null,
                    null,
                    BigDecimal.valueOf(10000),
                    BigDecimal.ZERO,
                    BigDecimal.valueOf(10000)
            );

            // when & then
            mockMvc.perform(post("/api/v1/raffles/{raffleId}/entries", raffleId)
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("래플 결과 조회 API")
    class GetRaffleResult {

        @Test
        @DisplayName("당첨 결과를 정상적으로 조회하면 200 OK를 반환한다")
        void success() throws Exception {
            // given
            UUID raffleId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            
            RaffleResultResponse responseDto = new RaffleResultResponse(
                    UUID.randomUUID(), raffleId, userId, RaffleResultStatus.WIN, LocalDateTime.now()
            );

            when(raffleResultService.getResult(raffleId, userId)).thenReturn(responseDto);

            // when & then
            mockMvc.perform(get("/api/v1/raffles/{raffleId}/results", raffleId)
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.data.status").value("WIN"));
        }
    }
}

