package com.omc.product.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.common.util.UuidV7Generator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 재고 (p_inventories)
 *
 * MVP: Product Service 내 패키지로 통합 관리
 * TODO: Inventory Service 분리 예정 (트러블슈팅 기간)
 *
 * productId: Inventory Service 분리 대비 논리 참조 (FK 없음)
 *
 * available_quantity: DB GENERATED ALWAYS AS (total - sold) STORED
 * → JPA @Column(insertable=false, updatable=false) 필수
 *
 * version: 래플 당첨자 일괄 처리 시 동시 업데이트 경합 방지
 *
 * confirmDeduct: 결제 완료(payment.completed) 이벤트 수신 후 호출
 * SAGA 케이스 B: OptimisticLockException 발생 시 stock.failed 발행
 */
@Getter
@Entity
@Table(name = "p_inventories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseEntity {

    @Id
    @Column(name = "inventory_id")
    private UUID inventoryId;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "sold_quantity", nullable = false)
    private int soldQuantity;

    @Column(name = "available_quantity", insertable = false, updatable = false)
    private int availableQuantity;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder(access = AccessLevel.PRIVATE)
    private Inventory(UUID productId, int totalQuantity) {
        this.inventoryId = UuidV7Generator.generate();
        this.productId = productId;
        this.totalQuantity = totalQuantity;
        this.soldQuantity = 0;
    }

    public static Inventory create(UUID productId, int initialQuantity) {
        return Inventory.builder()
                .productId(productId)
                .totalQuantity(initialQuantity)
                .build();
    }

    public void confirmDeduct(int quantity) {
        if (this.soldQuantity + quantity > this.totalQuantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.soldQuantity += quantity;
    }

    public void updateTotalQuantity(int newTotalQuantity) {
        if (newTotalQuantity < this.soldQuantity) {
            throw new IllegalArgumentException(
                    "총 재고는 판매 수량(" + this.soldQuantity + ")보다 적을 수 없습니다."
            );
        }
        this.totalQuantity = newTotalQuantity;
    }
}