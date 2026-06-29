package com.omc.drop.domain.repository;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.exception.DropNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface DropRepository extends JpaRepository<Drop, UUID> {

    Page<Drop> findAllByStatus(DropStatus status, Pageable pageable);

    boolean existsByProductIdAndStatusIn(UUID productId, List<DropStatus> statuses);

    List<Drop> findByStatusAndStartAtLessThanEqual(DropStatus status, LocalDateTime now);

    List<Drop> findByStatus(DropStatus status);

    List<Drop> findByStatusAndEndAtGreaterThanEqual(DropStatus status, LocalDateTime from);

    List<Drop> findByStatusAndEndAtLessThanEqual(DropStatus status, LocalDateTime now);

    // 조건부 UPDATE — 멀티 인스턴스 환경에서 정확히 1개 인스턴스만 전이를 처리하도록 보장
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Drop d SET d.status = :newStatus WHERE d.dropId = :dropId AND d.status = :currentStatus AND d.deletedAt IS NULL")
    int updateStatusConditionally(@Param("dropId") UUID dropId,
                                  @Param("currentStatus") DropStatus currentStatus,
                                  @Param("newStatus") DropStatus newStatus);

    default Drop getByIdOrThrow(UUID dropId) {
        return findById(dropId).orElseThrow(DropNotFoundException::new);
    }
}
