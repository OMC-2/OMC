package com.omc.product.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.common.handler.GlobalExceptionHandler;
import com.omc.product.application.service.ProductService;
import com.omc.product.domain.enums.ProductStatus;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.ProductAlreadyDeletedException;
import com.omc.product.domain.exception.ProductNotFoundException;
import com.omc.product.infrastructure.config.SecurityConfig;
import com.omc.product.presentation.dto.request.ProductCreateRequest;
import com.omc.product.presentation.dto.request.ProductUpdateRequest;
import com.omc.product.presentation.dto.response.ProductResponse;
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
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "gateway.secret=test-gateway-secret")
class ProductAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean
    ProductService productService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final String GW_SECRET = "test-gateway-secret";

    private ProductResponse productResponse() {
        return new ProductResponse(
                PRODUCT_ID, "Switch 2", "설명",
                "Nintendo", "게이밍 기기", 648000L,
                null, ProductStatus.ACTIVE, 10, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("상품 등록 API")
    class CreateProductApi {

        @Test
        @DisplayName("ADMIN이면 201을 반환한다")
        void createProduct_success() throws Exception {

            ProductCreateRequest request = new ProductCreateRequest(
                    "Switch 2", "설명", 648000L,
                    "Nintendo", "게이밍 기기", null, 10
            );

            given(productService.createProduct(any())).willReturn(productResponse());

            mockMvc.perform(post("/api/v1/admin/products")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.data.name").value("Switch 2"));
        }

        @Test
        @DisplayName("USER 권한이면 403을 반환한다")
        void createProduct_forbidden() throws Exception {

            ProductCreateRequest request = new ProductCreateRequest(
                    "Switch 2", "설명", 648000L,
                    "Nintendo", "게이밍 기기", null, 10
            );

            mockMvc.perform(post("/api/v1/admin/products")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "USER")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("가격이 0 이하이면 400을 반환한다")
        void createProduct_invalidPrice() throws Exception {

            String invalidRequest = """
                {
                    "name": "Switch 2",
                    "price": 0,
                    "brand": "Nintendo",
                    "category": "게이밍 기기",
                    "initialQuantity": 10
                }
                """;

            mockMvc.perform(post("/api/v1/admin/products")
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("상품 수정 API")
    class UpdateProductApi {

        @Test
        @DisplayName("ADMIN이면 200을 반환한다")
        void updateProduct_success() throws Exception {

            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 상품명", null, 648000L, null, null
            );

            ProductResponse response = new ProductResponse(
                    PRODUCT_ID, "수정된 상품명", "설명",
                    "Nintendo", "게이밍 기기", 648000L,
                    null, ProductStatus.ACTIVE, 10, LocalDateTime.now()
            );

            given(productService.updateProduct(any(), any())).willReturn(response);

            mockMvc.perform(patch("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("수정된 상품명"))
                    .andExpect(jsonPath("$.data.price").value(648000));
        }

        @Test
        @DisplayName("존재하지 않는 상품이면 404를 반환한다")
        void updateProduct_notFound() throws Exception {

            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 상품명", null, null, null, null
            );

            given(productService.updateProduct(any(), any()))
                    .willThrow(new ProductNotFoundException());

            mockMvc.perform(patch("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("상품 수정 시 진행 중인 Drop이 있으면 409를 반환한다")
        void updateProduct_activeDropExists() throws Exception {

            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 상품명", null, null, null, null
            );

            given(productService.updateProduct(any(), any()))
                    .willThrow(new ActiveDropExistsException());

            mockMvc.perform(patch("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());  // 409
        }
    }

    @Nested
    @DisplayName("상품 삭제 API")
    class DeleteProductApi {

        @Test
        @DisplayName("ADMIN이면 200을 반환한다")
        void deleteProduct_success() throws Exception {

            willDoNothing().given(productService).deleteProduct(any(), any());

            mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("이미 삭제된 상품이면 400을 반환한다")
        void deleteProduct_alreadyDeleted() throws Exception {

            willThrow(new ProductAlreadyDeletedException())
                    .given(productService).deleteProduct(any(), any());

            mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("X-User-Id 헤더가 없으면 403을 반환한다")
        void deleteProduct_missingHeader() throws Exception {

            mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Role", "ADMIN")
                            .with(csrf()))
                    .andExpect(status().isForbidden());  // 400 → 403
        }

        @Test
        @DisplayName("상품 삭제 시 진행 중인 Drop이 있으면 409를 반환한다")
        void deleteProduct_activeDropExists() throws Exception {

            willThrow(new ActiveDropExistsException())
                    .given(productService).deleteProduct(any(), any());

            mockMvc.perform(delete("/api/v1/admin/products/{productId}", PRODUCT_ID)
                            .header("X-Gateway-Secret", GW_SECRET)
                            .header("X-User-Id", UUID.randomUUID().toString())
                            .header("X-User-Role", "ADMIN")
                            .with(csrf()))
                    .andExpect(status().isConflict());
        }
    }
}