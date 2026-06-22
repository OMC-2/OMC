package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.payment.application.port.out.PaymentGatewayCommand;
import com.omc.payment.application.port.out.PaymentGatewayPort;
import com.omc.payment.application.port.out.PaymentGatewayResult;
import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.*;
import com.omc.payment.domain.exception.PaymentErrorCode;
import com.omc.payment.domain.exception.PaymentGatewayConnectionException;
import com.omc.payment.domain.exception.PaymentGatewayRequestException;
import com.omc.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentCoreService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentOutboxService paymentOutboxService;

    @Transactional
    public Payment confirmPayment(
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
        validatePaymentAmounts(originalAmount, discountAmount, finalAmount);

        // 멱등성 방어 로직
        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existingPayment != null) {
            return existingPayment;
        }

        Payment payment = paymentRepository.save(
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
                        Provider.TOSS,
                        PaymentMethod.CARD
                )
        );
        payment.startConfirming();

        // Mocking을 위한 랜덤 결제 식별자 Fallback
        String resolvedProviderPaymentId = providerPaymentId == null || providerPaymentId.isBlank()
                ? UUID.randomUUID().toString()
                : providerPaymentId;

        return confirmWithGateway(payment, orderId, finalAmount, resolvedProviderPaymentId);
    }

    @Transactional
    public Payment confirmBillingPayment(
            UUID orderId,
            UUID entryId,
            UUID raffleId,
            UUID productId,
            UUID couponId,
            UUID userId,
            String billingKeyId,
            String customerKey,
            Long originalAmount,
            Long discountAmount,
            Long finalAmount
    ) {
        validatePaymentAmounts(originalAmount, discountAmount, finalAmount);

        if (billingKeyId == null || billingKeyId.isBlank()) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED, "자동결제를 위한 billingKey가 없습니다.");
        }

        Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existingPayment != null) {
            return existingPayment;
        }

        Payment payment = paymentRepository.save(
                Payment.create(
                        orderId,
                        null,
                        entryId,
                        raffleId,
                        productId,
                        couponId,
                        userId,
                        SalesType.RAFFLE,
                        originalAmount,
                        discountAmount,
                        Provider.TOSS,
                        PaymentMethod.CARD
                )
        );

        payment.startConfirming();

        return confirmBillingWithGateway(payment, billingKeyId, customerKey, orderId, finalAmount);
    }

    /*
    * 외부 동기 호출 API 전용
    * */
    @Transactional
    public Payment cancelPaymentByPaymentId(
            UUID paymentId,
            UUID requesterId,
            String requesterRole,
            CancellationCode cancellationCode,
            String reason
    ) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getPaymentStatus() == PaymentStatus.CANCELED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        return switch (requesterRole) {
            case "ADMIN" -> cancel(payment, CancellationCode.ADMIN_CANCEL, reason);
            case "USER" -> cancelByUser(payment, requesterId, reason);
            default -> throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        };
    }

    /*
     * 이벤트 비동기 호출 전용
     * */
    @Transactional
    public void cancelPaymentByOrderId(UUID orderId, CancellationCode cancellationCode, String reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getPaymentStatus() == PaymentStatus.CANCELED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            return;
        }

        String resolvedReason = reason == null || reason.isBlank()
                ? cancellationCode.name()
                : reason;

        cancel(payment, cancellationCode, resolvedReason);
    }

    private Payment cancelByUser(Payment payment, UUID requesterId, String reason) {
        if (!requesterId.equals(payment.getUserId())) {
            throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }

        return cancel(payment, CancellationCode.USER_CANCEL, reason);
    }

    private Payment cancel(Payment payment, CancellationCode cancellationCode, String reason) {
        String providerCancellationId = cancelWithGateway(payment, reason);
        payment.cancel(providerCancellationId, cancellationCode, reason);
        paymentOutboxService.saveRefundDone(payment);
        return payment;
    }

    // confirmPayment PG 연동 로직 분리
    private Payment confirmWithGateway(
            Payment payment,
            UUID orderId,
            Long finalAmount,
            String providerPaymentId
    ) {
        try {
            PaymentGatewayResult.Confirm result = paymentGatewayPort.confirmPayment(
                    new PaymentGatewayCommand.Confirm(
                            providerPaymentId,
                            orderId.toString(),
                            finalAmount
                    )
            );
            payment.approve(result.providerPaymentId());
            paymentOutboxService.savePaymentCompleted(payment);
            return payment;
        } catch (PaymentGatewayRequestException e) {
            /* FAILED 처리 */
            payment.fail(e.getProviderCode(), e.getMessage());
            paymentOutboxService.savePaymentFailed(payment);
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED, e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            /* UNKNOWN 처리, 추후 재처리 필요 */
            payment.markUnknown();
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_CONNECTION_FAILED, e.getMessage());
        }
    }

    // confirmBillingPayment PG 연동 로직 분리
    private Payment confirmBillingWithGateway(
            Payment payment,
            String billingKeyId,
            String customerKey,
            UUID orderId,
            Long finalAmount
    ) {
        try {
            String resolvedCustomerKey = customerKey == null || customerKey.isBlank()
                    ? UUID.randomUUID().toString()
                    : customerKey;

            PaymentGatewayResult.Confirm result = paymentGatewayPort.confirmBillingPayment(
                    new PaymentGatewayCommand.ConfirmBilling(
                            billingKeyId,
                            customerKey,
                            orderId.toString(),
                            "래플 자동결제",
                            finalAmount
                    )
            );
            payment.approve(result.providerPaymentId());
            paymentOutboxService.savePaymentCompleted(payment);
            return payment;
        } catch (PaymentGatewayRequestException e) {
            payment.fail(e.getProviderCode(), e.getMessage());
            paymentOutboxService.savePaymentFailed(payment);
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED, e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            payment.markUnknown();
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_CONNECTION_FAILED, e.getMessage());
        }
    }

    // cancelPayment PG 연동 로직 분리
    private String cancelWithGateway(Payment payment, String cancelReason) {
        try {
            PaymentGatewayResult.Cancel result = paymentGatewayPort.cancelPayment(
                    new PaymentGatewayCommand.Cancel(
                            payment.getProviderPaymentId(),
                            cancelReason,
                            payment.getFinalAmount()
                    )
            );
            return result.providerCancellationId();
        } catch (PaymentGatewayRequestException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_REQUEST_FAILED, e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_CONNECTION_FAILED, e.getMessage());
        }
    }

    // PG 연동 전 검증
    private void validatePaymentAmounts(Long originalAmount, Long discountAmount, Long finalAmount) {
        // 금액 검증
        if (originalAmount - discountAmount != finalAmount) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }
}
