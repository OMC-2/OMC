package com.omc.payment.application.service;

import com.omc.payment.domain.entity.PaymentInboxEvent;
import com.omc.payment.domain.repository.PaymentInboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInboxService {
    /*
    * 이벤트 구독 시 멱등성 보장 INBOX 패턴
    * */
    private final PaymentInboxEventRepository paymentInboxEventRepository;

    public boolean isAlreadyProcessed(String eventId, String topic) {
        try {
            paymentInboxEventRepository.saveAndFlush(PaymentInboxEvent.create(eventId, topic));
            return false;
        } catch (DataIntegrityViolationException e) {
            return true;
        }
    }
}