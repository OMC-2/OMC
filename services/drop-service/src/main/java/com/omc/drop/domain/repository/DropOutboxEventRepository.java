package com.omc.drop.domain.repository;

import com.omc.drop.domain.entity.DropOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DropOutboxEventRepository extends JpaRepository<DropOutboxEvent, UUID> {

    // FOR UPDATE SKIP LOCKED: 다중 인스턴스가 동시에 폴링해도 같은 행을 중복 처리하지 않음.
    // 한 인스턴스가 잡은 행은 트랜잭션이 끝날 때까지 잠기고, 나머지 인스턴스는 그 행을 건너뜀.
    // native query는 Hibernate default_schema 적용을 받지 않아 스키마를 명시한다.
    @Query(value = """
            SELECT * FROM drop_db.p_drop_outbox_events
            WHERE status = 'INIT'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<DropOutboxEvent> findPendingWithLock(@Param("limit") int limit);
}
