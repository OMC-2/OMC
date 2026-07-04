package com.omc.product.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.common.util.UuidV7Generator;
import com.omc.product.domain.exception.InsufficientStockException;
import com.omc.product.domain.exception.QuantityBelowSoldException;
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
 * 트러블슈팅 기간 중 Inventory Service 분리 타당성을 재검토했으나,
 * 현재 트래픽 패턴(Kafka 컨슈머 동시성 3)에서는 CPU/Lag 모두 여유가 있어 분리를 보류함
 * (재고 확정 차감 동시성 결함은 Semaphore·DLT로 완화 조치했으나, 근본 원인인
 * HikariCP 커넥션 미반납 현상은 미해결 상태라 다음 스프린트에서 재논의 예정)
 *
 * productId: 향후 분리 가능성을 대비한 논리 참조 (FK 없음)
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
            throw new InsufficientStockException();
        }

        this.soldQuantity += quantity;
    }

    public void updateTotalQuantity(int newTotalQuantity) {
        if (newTotalQuantity < this.soldQuantity) {
            throw new QuantityBelowSoldException();
        }
        this.totalQuantity = newTotalQuantity;
    }
}