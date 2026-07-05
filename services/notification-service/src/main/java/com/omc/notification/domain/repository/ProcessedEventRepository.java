package com.omc.notification.domain.repository;

import com.omc.notification.domain.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    List<ProcessedEvent> findAllByEventIdInAndTopic(List<String> eventIds, String topic);
}
