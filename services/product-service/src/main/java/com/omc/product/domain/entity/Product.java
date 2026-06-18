package com.omc.product.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.product.domain.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * 상품 (p_products)
 *
 * - Soft Delete 적용 (deleted_at IS NULL 기준 조회)
 * - 주문 내역에서 상품명 스냅샷 유지 목적
 */
@Getter
@Entity
@Table(name = "p_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("deleted_at IS NULL")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false, length = 50)
    private String brand;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    @Builder(access = AccessLevel.PRIVATE)
    private Product(UUID productId, String name, String description,
                    Long price, String brand, String category,
                    String imageUrl, ProductStatus status) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.brand = brand;
        this.category = category;
        this.imageUrl = imageUrl;
        this.status = status;
    }

    public static Product create(String name, String description, Long price,
                                 String brand, String category, String imageUrl) {
        return Product.builder()
                .name(name)
                .description(description)
                .price(price)
                .brand(brand)
                .category(category)
                .imageUrl(imageUrl)
                .status(ProductStatus.ACTIVE)
                .build();
    }

    public void update(String name, String description, Long price,
                       String imageUrl, ProductStatus status) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (price != null) this.price = price;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (status != null) this.status = status;
    }

    public void delete(UUID deletedBy) {
        this.status = ProductStatus.DELETED;
        softDelete(deletedBy);
    }

    public boolean isDeleted() {

        return this.status == ProductStatus.DELETED;
    }
}