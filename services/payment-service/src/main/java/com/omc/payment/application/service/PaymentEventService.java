package com.omc.payment.application.service;

import com.omc.payment.application.event.consumer.OrderCreatedEvent;
import com.omc.payment.application.event.consumer.RefundRequestedEvent;
import com.omc.payment.application.event.consumer.StockFailedEvent;
import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.infrastructure.config.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventService {

    private final PaymentInboxService paymentInboxService;
    private final PaymentCoreService paymentCoreService;

    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        if (paymentInboxService.isAlreadyProcessed(event.eventId(), KafkaTopics.ORDER_CREATED)) {
            log.info("이미 처리된 order.created 이벤트입니다. eventId={}", event.eventId());
            return;
        }

        if ("RAFFLE".equalsIgnoreCase(event.orderType())) {
            paymentCoreService.confirmBillingPayment(
                    event.orderId(),
                    event.entryId(),
                    event.raffleId(),
                    event.productId(),
                    event.couponId(),
                    event.userId(),
                    event.billingKeyId(),
                    null,
                    event.originalAmount(),
                    event.discountAmount(),
                    event.finalAmount()
            );
            return;
        }

        paymentCoreService.confirmPayment(
                event.orderId(),
                event.dropId(),
                event.productId(),
                event.couponId(),
                event.userId(),
                event.originalAmount(),
                event.discountAmount(),
                event.finalAmount(),
                UUID.randomUUID().toString()
        );
    }

    @Transactional
    public void handleRefundRequested(RefundRequestedEvent event) {
        if (paymentInboxService.isAlreadyProcessed(event.eventId(), KafkaTopics.REFUND_REQUESTED)) {
            log.info("이미 처리한 refund.requested 이벤트입니다. eventId={}", event.eventId());
            return;
        }

        paymentCoreService.cancelPaymentByOrderId(
                event.orderId(),
                null,
                event.reason()
        );
    }

    @Transactional
    public void handleStockFailed(StockFailedEvent event) {
        if (paymentInboxService.isAlreadyProcessed(event.eventId(), KafkaTopics.STOCK_FAILED)) {
            log.info("이미 처리한 stock.failed 이벤트입니다. eventId={}", event.eventId());
            return;
        }

        paymentCoreService.cancelPaymentByOrderId(
                event.orderId(),
                CancellationCode.STOCK_DEDUCT_FAILED,
                "재고 차감 실패"
        );
    }
}
