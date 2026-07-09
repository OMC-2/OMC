package com.omc.coupon.domain.repository;

import com.omc.coupon.domain.entity.OutboxEvent;
import com.omc.coupon.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.aggregateId IN :aggregateIds")
    void deleteByAggregateIdIn(@Param("aggregateIds") List<UUID> aggregateIds);
}
