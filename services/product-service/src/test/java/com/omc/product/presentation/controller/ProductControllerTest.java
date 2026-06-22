package com.omc.product.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.product.application.service.ProductService;
import com.omc.product.domain.enums.ProductStatus;
import com.omc.product.domain.exception.ProductNotFoundException;
import com.omc.product.infrastructure.config.SecurityConfig;
import com.omc.product.presentation.dto.response.ProductResponse;
import com.omc.product.presentation.dto.response.ProductSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String GW_SECRET = "test-gateway-secret";

    @Nested
    @DisplayName("상품 목록 조회 API")
    class GetProductsApi {
        @Test
        @DisplayName("비회원도 200을 반환한다")
        void getProducts_success_anonymous() throws Exception {

            ProductSummaryResponse summary = new ProductSummaryResponse(
                    PRODUCT_ID, "Switch 2", "Nintendo",
                    "게이밍 기기", 648000L, null, ProductStatus.ACTIVE
            );
            given(productService.getProducts(any(), any(), any(), any()))
                    .willReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 10), 1));

            mockMvc.perform(get("/api/v1/products")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .param("category", "게이밍 기기"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].name").value("Switch 2"))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("상품 상세 조회 API")
    class GetProductApi {

        @Test
        @DisplayName("상품을 조회하면 200을 반환한다")
        void getProduct_success_anonymous() throws Exception {

            ProductResponse response = new ProductResponse(
                    PRODUCT_ID, "Switch 2", "설명",
                    "Nintendo", "게이밍 기기", 648000L,
                    null, ProductStatus.ACTIVE, 8, LocalDateTime.now()
            );

            given(productService.getProduct(PRODUCT_ID)).willReturn(response);

            mockMvc.perform(get("/api/v1/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID.toString()))
                    .andExpect(jsonPath("$.data.availableQuantity").value(8));
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 404를 반환한다")
        void getProduct_notFound() throws Exception {

            given(productService.getProduct(PRODUCT_ID))
                    .willThrow(new ProductNotFoundException());

            mockMvc.perform(get("/api/v1/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET))
                    .andExpect(status().isNotFound());
        }
    }
}