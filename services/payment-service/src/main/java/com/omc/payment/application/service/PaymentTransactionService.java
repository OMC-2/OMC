package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.*;
import com.omc.payment.domain.exception.PaymentErrorCode;
import com.omc.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxService paymentOutboxService;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Payment findByOrderId(UUID orderId){
        return paymentRepository.findByOrderId(orderId).orElse(null);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Payment findById(UUID paymentId) {
        return paymentRepository.findById(paymentId).orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createDropPayment(
            UUID orderId,
            UUID dropId,
            UUID productId,
            UUID couponId,
            UUID userId,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount,
            String providerPaymentId
    ) {
        return paymentRepository.save(
                Payment.create(
                        orderId,
                        dropId,
                        null,
                        null,
                        productId,
                        couponId,
                        userId,
                        SalesType.DROP,
                        originalAmount,
                        discountAmount,
                        finalAmount,
                        Provider.TOSS,
                        providerPaymentId,
                        PaymentMethod.CARD
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment createBillingPayment(
            UUID orderId,
            UUID entryId,
            UUID raffleId,
            UUID productId,
            UUID couponId,
            UUID userId,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount
    ) {
        return paymentRepository.save(
                Payment.create(
                        orderId,
                        null,
                        raffleId,
                        entryId,
                        productId,
                        couponId,
                        userId,
                        SalesType.RAFFLE,
                        originalAmount,
                        discountAmount,
                        finalAmount,
                        Provider.TOSS,
                        null,
                        PaymentMethod.CARD
                )
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment markConfirming(UUID paymentId) {
        Payment payment = getPayment(paymentId);
        payment.startConfirming();
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment approveAndSaveOutbox(UUID paymentId, String providerPaymentId) {
        Payment payment = getPayment(paymentId);
        payment.approve(providerPaymentId);
        paymentOutboxService.savePaymentCompleted(payment);
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment failAndSaveOutbox(UUID paymentId, String failureCode, String failureMessage) {
        Payment payment = getPayment(paymentId);
        payment.fail(failureCode, failureMessage);
        paymentOutboxService.savePaymentFailed(payment);
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment markUnknown(UUID paymentId) {
        Payment payment = getPayment(paymentId);
        payment.markUnknown();
        return payment;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment cancelAndSaveOutbox(
            UUID paymentId,
            String providerCancellationId,
            CancellationCode cancellationCode,
            String reason
    ) {
        Payment payment = getPayment(paymentId);
        if (payment.getPaymentStatus() == PaymentStatus.CANCELED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            return payment;
        }
        payment.cancel(providerCancellationId, cancellationCode, reason);
        paymentOutboxService.saveRefundDone(payment);
        return payment;
    }

    private Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(
                () -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND)
        );
    }
}
