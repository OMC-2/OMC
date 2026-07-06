package com.omc.product.application.service;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.event.producer.ProductUpdatedEvent;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.ProductNotFoundException;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.ProductRepository;
import com.omc.product.infrastructure.client.DropFeignClient;
import com.omc.product.infrastructure.client.ActiveDropResponse;
import com.omc.product.presentation.dto.request.ProductCreateRequest;
import com.omc.product.presentation.dto.request.ProductUpdateRequest;
import com.omc.product.presentation.dto.response.ProductResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private DropFeignClient dropFeignClient;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProductService productService;

    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private ProductUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        updateRequest = new ProductUpdateRequest("수정된 상품명", null, null, null, null);
    }

    @Test
    @DisplayName("상품을 등록하면 상품과 재고를 함께 생성한다")
    void createProduct_success() {

        ProductCreateRequest request = new ProductCreateRequest(
                "Switch 2", "설명", 648000L,
                "Nintendo", "게이밍 기기", null, 10
        );

        given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));
        given(inventoryRepository.save(any(Inventory.class))).willAnswer(inv -> inv.getArgument(0));

        ProductResponse response = productService.createProduct(request);

        assertThat(response.name()).isEqualTo("Switch 2");
        assertThat(response.price()).isEqualTo(648000L);
        verify(productRepository).save(any(Product.class));
        verify(inventoryRepository).save(any(Inventory.class));
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 ProductNotFoundException을 발생시킨다")
    void getProduct_notFound() {

        UUID unknownId = UUID.randomUUID();
        given(productRepository.findById(unknownId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(unknownId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("진행 중인 Drop이 있으면 상품 수정 시 ActiveDropExistsException을 발생시킨다")
    void updateProduct_activeDropExists() {

        given(dropFeignClient.hasActiveDrop(any()))
                .willReturn(ApiResponse.success(new ActiveDropResponse(true)));

        assertThatThrownBy(() -> productService.updateProduct(PRODUCT_ID, updateRequest))
                .isInstanceOf(ActiveDropExistsException.class);
    }

    @Test
    @DisplayName("진행 중인 Drop이 있으면 상품 삭제 시 ActiveDropExistsException을 발생시킨다")
    void deleteProduct_activeDropExists() {

        given(dropFeignClient.hasActiveDrop(any()))
                .willReturn(ApiResponse.success(new ActiveDropResponse(true)));

        assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_ID, UUID.randomUUID()))
                .isInstanceOf(ActiveDropExistsException.class);
    }

    @Test
    @DisplayName("상품을 삭제하면 소프트 삭제되고 캐시 무효화를 위한 ProductUpdatedEvent를 발행한다")
    void deleteProduct_success() {

        given(dropFeignClient.hasActiveDrop(any()))
                .willReturn(ApiResponse.success(new ActiveDropResponse(false)));

        Product product = Product.create(
                "Switch 2", "설명", 648000L, "Nintendo", "게이밍 기기", null
        );
        given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

        productService.deleteProduct(PRODUCT_ID, UUID.randomUUID());

        assertThat(product.isDeleted()).isTrue();
        verify(eventPublisher).publishEvent(any(ProductUpdatedEvent.class));
    }
}