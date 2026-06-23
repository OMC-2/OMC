package com.omc.coupon.application.service;

import com.omc.coupon.domain.entity.ProcessedEvent;
import com.omc.coupon.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessedEventIdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * 별도 트랜잭션(REQUIRES_NEW)으로 ProcessedEvent를 INSERT한다.
     * PK 중복 시 DataIntegrityViolationException을 호출자에게 전파하고,
     * 이 내부 트랜잭션만 롤백되어 외부 트랜잭션은 영향받지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId, String topic) {
        processedEventRepository.saveAndFlush(ProcessedEvent.create(eventId, topic));
    }
}
