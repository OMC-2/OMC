package com.omc.product.application.service;

import com.omc.product.application.event.ProductUpdatedEvent;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.domain.exception.ProductAlreadyDeletedException;
import com.omc.product.domain.exception.ProductNotFoundException;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.ProductRepository;
import com.omc.product.infrastructure.client.ActiveDropResponse;
import com.omc.product.infrastructure.client.DropInternalClient;
import com.omc.product.presentation.dto.request.ProductCreateRequest;
import com.omc.product.presentation.dto.request.ProductUpdateRequest;
import com.omc.product.presentation.dto.response.ProductResponse;
import com.omc.product.presentation.dto.response.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 상품 관리 서비스
 *
 * 주요 책임
 * - 상품 및 재고 생성
 * - 상품 조회, 수정, 삭제
 * - 상품 상세 조회 캐시 관리
 *
 * 캐시 전략
 * - 상품 상세 조회는 Redis 캐시를 사용
 * - 상품 수정 및 삭제 시 ProductUpdatedEvent를 발행
 * - 캐시 무효화는 트랜잭션 커밋 이후 이벤트 리스너에서 수행
 *
 * 삭제 및 수정 정책
 * - 상품 삭제는 Soft Delete 방식으로 처리
 * - 삭제된 상품은 조회 및 수정할 수 없음
 * - 진행 중인 Drop이 존재하는 상품은 삭제 및 수정할 수 없음
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DropInternalClient dropInternalClient;

    @Transactional
    public ProductResponse createProduct(ProductCreateRequest request) {
        Product product = Product.create(
                request.name(), request.description(), request.price(),
                request.brand(), request.category(), request.imageUrl()
        );
        productRepository.save(product);

        Inventory inventory = Inventory.create(product.getProductId(), request.initialQuantity());
        inventoryRepository.save(inventory);

        return ProductResponse.of(product, inventory);
    }

    public Page<ProductSummaryResponse> getProducts(String category, String brand,
                                                    String keyword, Pageable pageable) {
        return productRepository
                .findAllWithFilter(category, brand, keyword, pageable)
                .map(ProductSummaryResponse::from);
    }

    @Cacheable(value = "product", key = "#productId")
    public ProductResponse getProduct(UUID productId) {
        Product product = findActiveProduct(productId);
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);
        return ProductResponse.of(product, inventory);
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, ProductUpdateRequest request) {
        ActiveDropResponse response = dropInternalClient.hasActiveDrop(productId).getData();
        if (response.hasActiveDrop()) {
            throw new ActiveDropExistsException();
        }
        Product product = findActiveProduct(productId);
        product.update(request.name(), request.description(), request.price(),
                request.imageUrl(), request.status());
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);

        eventPublisher.publishEvent(new ProductUpdatedEvent(productId));

        return ProductResponse.of(product, inventory);
    }

    @Transactional
    public void deleteProduct(UUID productId, UUID deletedBy) {
        ActiveDropResponse response = dropInternalClient.hasActiveDrop(productId).getData();
        if (response.hasActiveDrop()) {
            throw new ActiveDropExistsException();
        }
        Product product = findActiveProduct(productId);
        product.delete(deletedBy);
    }

    private Product findActiveProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
        if (product.isDeleted()) {
            throw new ProductAlreadyDeletedException();
        }
        return product;
    }
}