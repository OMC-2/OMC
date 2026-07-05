package com.omc.notification.application.service;

import com.omc.notification.domain.entity.ProcessedEvent;
import com.omc.notification.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessedEventIdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId, String topic) {
        processedEventRepository.saveAndFlush(ProcessedEvent.create(eventId, topic));
    }

    @Transactional
    public Set<String> filterAndMarkProcessed(List<String> eventIds, String topic) {
        if (eventIds.isEmpty()) return Set.of();

        Set<String> alreadyProcessed = processedEventRepository
                .findAllByEventIdInAndTopic(eventIds, topic)
                .stream()
                .map(ProcessedEvent::getEventId)
                .collect(Collectors.toSet());

        List<ProcessedEvent> toInsert = eventIds.stream()
                .distinct()
                .filter(id -> !alreadyProcessed.contains(id))
                .map(id -> ProcessedEvent.create(id, topic))
                .toList();

        if (!toInsert.isEmpty()) {
            processedEventRepository.saveAll(toInsert);
        }

        return alreadyProcessed;
    }
}
