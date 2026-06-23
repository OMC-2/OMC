package com.omc.drop.domain.entity;

import com.omc.common.entity.BaseEntity;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.exception.DropNotOpenException;
import com.omc.drop.domain.exception.InvalidDropDateRangeException;
import com.omc.drop.domain.exception.InvalidDropStatusException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
    name = "p_drops",
    indexes = {
        @Index(name = "idx_drops_status_start", columnList = "status, start_at"),
        @Index(name = "idx_drops_status_end", columnList = "status, end_at")
    }
)
// 소프트딜리트된 드롭은 모든 JPA 쿼리에서 자동으로 제외. 삭제된 드롭 조회가 필요하면 네이티브 쿼리를 사용.
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drop extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID dropId;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DropStatus status;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int totalQty;

    @Column(nullable = false)
    private int holdTtlSec; // 선점 후 결제 대기 시간(초). Redis holds:{dropId} ZSet의 만료 epoch 계산에 사용

    @Builder(access = AccessLevel.PRIVATE)
    private Drop(UUID productId, LocalDateTime startAt, LocalDateTime endAt, int totalQty, int holdTtlSec) {
        this.productId = productId;
        this.status = DropStatus.SCHEDULED;
        this.startAt = startAt;
        this.endAt = endAt;
        this.totalQty = totalQty;
        this.holdTtlSec = holdTtlSec;
    }

    public static Drop create(UUID productId, LocalDateTime startAt, LocalDateTime endAt, int totalQty, int holdTtlSec) {
        validateDateRange(startAt, endAt);
        return Drop.builder()
                .productId(productId)
                .startAt(startAt)
                .endAt(endAt)
                .totalQty(totalQty)
                .holdTtlSec(holdTtlSec)
                .build();
    }

    public static void validateDateRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new InvalidDropDateRangeException();
        }
    }

    public void open() {
        if (this.status != DropStatus.SCHEDULED) {
            throw new InvalidDropStatusException();
        }
        this.status = DropStatus.OPEN;
    }

    public void close() {
        if (this.status != DropStatus.OPEN) {
            throw new InvalidDropStatusException();
        }
        this.status = DropStatus.CLOSED;
    }

    public void update(LocalDateTime startAt, LocalDateTime endAt, int totalQty, int holdTtlSec) {
        if (this.status != DropStatus.SCHEDULED) {
            throw new InvalidDropStatusException();
        }
        validateDateRange(startAt, endAt);
        this.startAt = startAt;
        this.endAt = endAt;
        this.totalQty = totalQty;
        this.holdTtlSec = holdTtlSec;
    }

    public void delete(UUID deletedBy) {
        if (this.status != DropStatus.SCHEDULED) {
            throw new InvalidDropStatusException();
        }
        softDelete(deletedBy);
    }

    public boolean isOpen() {
        return this.status == DropStatus.OPEN;
    }

    public void validateOpen() {
        if (!isOpen()) {
            throw new DropNotOpenException();
        }
    }
}
