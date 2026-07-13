package com.omc.drop.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "p_drop_purchase_reservations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_drop_reservations_drop_user",
        columnNames = {"drop_id", "user_id"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DropPurchaseReservation {

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "drop_id", nullable = false, updatable = false)
    private UUID dropId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "hold_expires_at", nullable = false, updatable = false)
    private LocalDateTime holdExpiresAt;

    @Column(name = "queue_number", nullable = false, updatable = false)
    private long queueNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder(access = AccessLevel.PRIVATE)
    private DropPurchaseReservation(UUID orderId, UUID dropId, UUID userId,
                                    UUID productId, LocalDateTime holdExpiresAt, long queueNumber) {
        this.orderId = orderId;
        this.dropId = dropId;
        this.userId = userId;
        this.productId = productId;
        this.holdExpiresAt = holdExpiresAt;
        this.queueNumber = queueNumber;
        this.createdAt = LocalDateTime.now();
    }

    public static DropPurchaseReservation create(UUID orderId, UUID dropId, UUID userId,
                                                 UUID productId, LocalDateTime holdExpiresAt, long queueNumber) {
        return DropPurchaseReservation.builder()
                .orderId(orderId)
                .dropId(dropId)
                .userId(userId)
                .productId(productId)
                .holdExpiresAt(holdExpiresAt)
                .queueNumber(queueNumber)
                .build();
    }
}
