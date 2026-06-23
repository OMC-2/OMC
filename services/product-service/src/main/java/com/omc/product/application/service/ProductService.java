package com.omc.product.application.service;

import com.omc.product.application.event.ProductUpdatedEvent;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.Product;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.domain.exception.ProductAlreadyDeletedException;
import com.omc.product.domain.exception.ProductNotFoundException;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.ProductRepository;
import com.omc.product.presentation.dto.request.ProductCreateRequest;
import com.omc.product.presentation.dto.request.ProductUpdateRequest;
import com.omc.product.presentation.dto.response.ProductResponse;
import com.omc.product.presentation.dto.response.ProductSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

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
        Product product = findActiveProduct(productId);
        product.update(request.name(), request.description(), request.price(),
                request.imageUrl(), request.status());
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);

        // 트랜잭션 커밋 후 캐시 무효화 이벤트 발행
        eventPublisher.publishEvent(new ProductUpdatedEvent(productId));

        return ProductResponse.of(product, inventory);
    }

    @Transactional
    public void deleteProduct(UUID productId, UUID deletedBy) {
        Product product = findActiveProduct(productId);
        product.delete(deletedBy);
        eventPublisher.publishEvent(new ProductUpdatedEvent(productId));
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