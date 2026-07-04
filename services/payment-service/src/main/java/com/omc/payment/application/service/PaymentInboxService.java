package com.omc.payment.application.service;

import com.omc.payment.domain.entity.PaymentInboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInboxService {
    /*
    * 이벤트 구독 시 멱등성 보장 INBOX 패턴
    * */
    private final PaymentInboxTransactionService paymentInboxTransactionService;

    public boolean isAlreadyProcessed(String eventId, String topic) {
        try {
            paymentInboxTransactionService.createProcessing(eventId, topic);
            return false;
        } catch (DataIntegrityViolationException e) {
            // 실패해서 재처리 로직을 탄 경우 상태 변경 FAILED -> PROCESSING
            return !paymentInboxTransactionService.retryIfFailed(eventId);
        }
    }

    public void markProcessed(String eventId) {
        paymentInboxTransactionService.markProcessed(eventId);
    }

    public void markFailed(String eventId) {
        paymentInboxTransactionService.markFailed(eventId);
    }
}