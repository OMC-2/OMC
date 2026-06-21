package com.omc.user.unit.controller;

import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.common.response.PageResponse;
import com.omc.user.application.service.AddressService;
import com.omc.user.domain.exception.AddressNotFoundException;
import com.omc.user.infrastructure.config.SecurityConfig;
import com.omc.user.presentation.controller.AddressController;
import com.omc.user.presentation.dto.response.AddressResponse;
import com.omc.user.presentation.dto.response.DefaultAddressResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AddressController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "gateway.secret=test-gateway-secret"
})
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    private static final String GW_SECRET = "test-gateway-secret";
    private static final String USER_ID   = "00000000-0000-0000-0000-000000000001";

    // =========================================================================
    // POST /api/v1/users/addresses
    // =========================================================================

    @Test
    void createAddress_success_returns201() throws Exception {
        UUID addressId = UUID.randomUUID();
        AddressResponse mockResponse = new AddressResponse(
                addressId, "노동완", "010-1234-5678", "12345", "서울시 강남구", "101호", false);
        given(addressService.createAddress(any(), any())).willReturn(mockResponse);

        mockMvc.perform(post("/api/v1/users/addresses")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "노동완",
                                    "phone": "010-1234-5678",
                                    "zipCode": "12345",
                                    "address": "서울시 강남구"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.addressId").value(addressId.toString()))
                .andExpect(jsonPath("$.data.recipientName").value("노동완"));
    }

    @Test
    void createAddress_missingRecipientName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/users/addresses")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "phone": "010-1234-5678",
                                    "zipCode": "12345",
                                    "address": "서울시 강남구"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON-001"));
    }

    @Test
    void createAddress_noUserIdHeader_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/users/addresses")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "노동완",
                                    "phone": "010-1234-5678",
                                    "zipCode": "12345",
                                    "address": "서울시 강남구"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/v1/users/addresses
    // =========================================================================

    @Test
    void getAddresses_success_returns200() throws Exception {
        AddressResponse item = new AddressResponse(
                UUID.randomUUID(), "노동완", "010-1234-5678", "12345", "서울시 강남구", null, false);
        PageResponse<AddressResponse> mockPage = new PageResponse<>(new PageImpl<>(List.of(item)));
        given(addressService.getAddresses(any(), any())).willReturn(mockPage);

        mockMvc.perform(get("/api/v1/users/addresses")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].recipientName").value("노동완"));
    }

    @Test
    void getAddresses_noUserIdHeader_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users/addresses")
                        .header("X-Gateway-Secret", GW_SECRET))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GET /api/v1/users/addresses/{addressId}
    // =========================================================================

    @Test
    void getAddress_success_returns200() throws Exception {
        UUID addressId = UUID.randomUUID();
        AddressResponse mockResponse = new AddressResponse(
                addressId, "노동완", "010-1234-5678", "12345", "서울시 강남구", null, false);
        given(addressService.getAddress(any(), any())).willReturn(mockResponse);

        mockMvc.perform(get("/api/v1/users/addresses/" + addressId)
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressId").value(addressId.toString()));
    }

    @Test
    void getAddress_notFound_returns404() throws Exception {
        given(addressService.getAddress(any(), any())).willThrow(new AddressNotFoundException());

        mockMvc.perform(get("/api/v1/users/addresses/" + UUID.randomUUID())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PATCH /api/v1/users/addresses/{addressId}
    // =========================================================================

    @Test
    void updateAddress_success_returns200() throws Exception {
        UUID addressId = UUID.randomUUID();
        AddressResponse mockResponse = new AddressResponse(
                addressId, "수정된이름", "010-9999-9999", "99999", "서울시 서초구", null, false);
        given(addressService.updateAddress(any(), any(), any())).willReturn(mockResponse);

        mockMvc.perform(patch("/api/v1/users/addresses/" + addressId)
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "수정된이름",
                                    "phone": "010-9999-9999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName").value("수정된이름"));
    }

    @Test
    void updateAddress_notFound_returns404() throws Exception {
        given(addressService.updateAddress(any(), any(), any())).willThrow(new AddressNotFoundException());

        mockMvc.perform(patch("/api/v1/users/addresses/" + UUID.randomUUID())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "recipientName": "수정된이름"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // DELETE /api/v1/users/addresses/{addressId}
    // =========================================================================

    @Test
    void deleteAddress_success_returns200() throws Exception {
        willDoNothing().given(addressService).deleteAddress(any(), any());

        mockMvc.perform(delete("/api/v1/users/addresses/" + UUID.randomUUID())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteAddress_notFound_returns404() throws Exception {
        willThrow(new AddressNotFoundException()).given(addressService).deleteAddress(any(), any());

        mockMvc.perform(delete("/api/v1/users/addresses/" + UUID.randomUUID())
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // PATCH /api/v1/users/addresses/{addressId}/default
    // =========================================================================

    @Test
    void setDefaultAddress_success_returns200() throws Exception {
        UUID addressId = UUID.randomUUID();
        given(addressService.setDefaultAddress(any(), any()))
                .willReturn(new DefaultAddressResponse(addressId, true));

        mockMvc.perform(patch("/api/v1/users/addresses/" + addressId + "/default")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isDefault").value(true))
                .andExpect(jsonPath("$.data.addressId").value(addressId.toString()));
    }

    @Test
    void setDefaultAddress_notFound_returns404() throws Exception {
        given(addressService.setDefaultAddress(any(), any())).willThrow(new AddressNotFoundException());

        mockMvc.perform(patch("/api/v1/users/addresses/" + UUID.randomUUID() + "/default")
                        .header("X-Gateway-Secret", GW_SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound());
    }
}
