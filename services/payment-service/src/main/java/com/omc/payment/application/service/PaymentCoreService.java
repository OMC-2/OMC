package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.ApiResponse;
import com.omc.payment.application.port.out.PaymentGatewayCommand;
import com.omc.payment.application.port.out.PaymentGatewayPort;
import com.omc.payment.application.port.out.PaymentGatewayResult;
import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.domain.enums.PaymentStatus;
import com.omc.payment.domain.exception.NonRetryablePaymentException;
import com.omc.payment.domain.exception.PaymentErrorCode;
import com.omc.payment.domain.exception.PaymentGatewayConnectionException;
import com.omc.payment.domain.exception.PaymentGatewayRequestException;
import com.omc.payment.domain.repository.PaymentRepository;
import com.omc.payment.infrastructure.client.CouponReserveRequest;
import com.omc.payment.infrastructure.client.CouponServiceClient;
import com.omc.payment.infrastructure.client.UserCouponResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentCoreService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PaymentOutboxService paymentOutboxService;
    private final CouponServiceClient couponServiceClient;
    private final PaymentIdempotencyService paymentIdempotencyService;
    private final PaymentTransactionService paymentTransactionService;

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
        // 멱등성 방어 로직
        Payment existingPayment = paymentTransactionService.findByOrderId(orderId);
        if (existingPayment != null) {
            return existingPayment;
        }

        Payment payment = paymentTransactionService.createDropPayment(
                orderId,
                dropId,
                productId,
                couponId,
                userId,
                originalAmount,
                discountAmount,
                finalAmount,
                providerPaymentId
        );

        try {
            validatePaymentAmounts(originalAmount, discountAmount, finalAmount);
            validateDropPayment(dropId, productId);
            reserveAndValidateCoupon(couponId, orderId, userId, originalAmount, discountAmount);

            Payment confirmingPayment = paymentTransactionService.markConfirming(payment.getPaymentId());
            return confirmWithGateway(confirmingPayment, orderId, finalAmount, providerPaymentId);
        } catch (NonRetryablePaymentException e) {
            return paymentTransactionService.failAndSaveOutbox(
                    payment.getPaymentId(),
                    e.getErrorCode().getCode(),
                    e.getMessage()
            );
        }
    }

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
        Payment existingPayment = paymentTransactionService.findByOrderId(orderId);
        if (existingPayment != null) {
            return existingPayment;
        }

        String resolvedCustomerKey = customerKey == null || customerKey.isBlank()
                ? UUID.randomUUID().toString()
                : customerKey;

        Payment payment = paymentTransactionService.createBillingPayment(
                orderId,
                entryId,
                raffleId,
                productId,
                couponId,
                userId,
                originalAmount,
                discountAmount,
                finalAmount
        );

        try {
            validatePaymentAmounts(originalAmount, discountAmount, finalAmount);
            validateRafflePayment(raffleId, entryId, productId, billingKeyId);
            reserveAndValidateCoupon(couponId, orderId, userId, originalAmount, discountAmount);

            Payment confirmingPayment = paymentTransactionService.markConfirming(payment.getPaymentId());
            return confirmBillingWithGateway(confirmingPayment, billingKeyId, resolvedCustomerKey, orderId, finalAmount);
        } catch (NonRetryablePaymentException e) {
            return paymentTransactionService.failAndSaveOutbox(
                    payment.getPaymentId(),
                    e.getErrorCode().getCode(),
                    e.getMessage()
            );
        }
    }

    /*
    * 외부 동기 호출 API 전용
    * */
    public Payment cancelPaymentByPaymentId(
            UUID paymentId,
            UUID userId,
            String userRole,
            CancellationCode cancellationCode,
            String reason
    ) {
        Payment payment = paymentTransactionService.findById(paymentId);
        if (payment == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }
        if (payment.getPaymentStatus() == PaymentStatus.CANCELED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        CancellationCode resolvedCancellationCode = resolveCancellationCode(payment, userId, userRole);
        String resolvedReason = reason == null || reason.isBlank()
                ? resolveCancellationReason(cancellationCode)
                : reason;

        String providerCancellationId = cancelWithGateway(payment, resolvedReason);
        return paymentTransactionService.cancelAndSaveOutbox(
                payment.getPaymentId(),
                providerCancellationId,
                resolvedCancellationCode,
                resolvedReason
        );
    }

    /*
     * 이벤트 비동기 호출 전용
     * */
    public void cancelPaymentByOrderId(UUID orderId, CancellationCode cancellationCode, String reason) {
        Payment payment = paymentTransactionService.findByOrderId(orderId);
        if (payment == null) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        }

        if (payment.getPaymentStatus() == PaymentStatus.CANCELED
                || payment.getPaymentStatus() == PaymentStatus.FAILED) {
            return;
        }

        String resolvedReason = reason == null || reason.isBlank()
                ? resolveCancellationReason(cancellationCode)
                : reason;

        String providerCancellationId = cancelWithGateway(payment, resolvedReason);
        paymentTransactionService.cancelAndSaveOutbox(
                payment.getPaymentId(),
                providerCancellationId,
                cancellationCode,
                resolvedReason
        );
    }

    private CancellationCode resolveCancellationCode(Payment payment, UUID userId, String userRole) {
        return switch (userRole) {
            case "ADMIN" -> CancellationCode.ADMIN_CANCEL;
            case "USER" -> {
                if (userId == null || !userId.equals(payment.getUserId())) {
                    throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
                }
                yield CancellationCode.USER_CANCEL;
            }
            default -> throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        };
    }

    private String resolveCancellationReason(CancellationCode cancellationCode) {
        return cancellationCode == null ? "결제 취소" : cancellationCode.name();
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
                            finalAmount,
                            paymentIdempotencyService.confirmKey(orderId)
                    )
            );
            if (result.providerPaymentId() == null || result.providerPaymentId().isBlank()) {
                throw new PaymentGatewayConnectionException("PG 결제 승인 응답에 결제 ID가 없습니다.");
            }
            return paymentTransactionService.approveAndSaveOutbox(payment.getPaymentId(), result.providerPaymentId());
        } catch (PaymentGatewayRequestException e) {
            /* FAILED 처리 */
            return paymentTransactionService.failAndSaveOutbox(payment.getPaymentId(), e.getProviderErrorCode(), e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            /* UNKNOWN 처리, 추후 재처리 필요 */
            return paymentTransactionService.markUnknown(payment.getPaymentId());
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
                            resolvedCustomerKey,
                            orderId.toString(),
                            "래플 자동 결제",
                            finalAmount,
                            paymentIdempotencyService.confirmKey(orderId)
                    )
            );
            if (result.providerPaymentId() == null || result.providerPaymentId().isBlank()) {
                throw new PaymentGatewayConnectionException("PG 결제 승인 응답에 결제 ID가 없습니다.");
            }
            return paymentTransactionService.approveAndSaveOutbox(payment.getPaymentId(), result.providerPaymentId());
        } catch (PaymentGatewayRequestException e) {
            return paymentTransactionService.failAndSaveOutbox(payment.getPaymentId(), e.getProviderErrorCode(), e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            return paymentTransactionService.markUnknown(payment.getPaymentId());
        }
    }

    // cancelPayment PG 연동 로직 분리
    private String cancelWithGateway(Payment payment, String cancelReason) {
        try {
            PaymentGatewayResult.Cancel result = paymentGatewayPort.cancelPayment(
                    new PaymentGatewayCommand.Cancel(
                            payment.getProviderPaymentId(),
                            cancelReason,
                            payment.getFinalAmount(),
                            paymentIdempotencyService.cancelKey(payment.getOrderId())
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
        if (originalAmount == null || finalAmount == null) {
            throw new NonRetryablePaymentException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "결제 금액은 필수입니다."
            );
        }
        long resolvedDiscountAmount = discountAmount == null ? 0L : discountAmount;
        if (originalAmount < 0 || resolvedDiscountAmount < 0 || finalAmount < 0) {
            throw new NonRetryablePaymentException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "결제 금액은 0 이상이어야 합니다."
            );
        }
        if (resolvedDiscountAmount > originalAmount || originalAmount - resolvedDiscountAmount != finalAmount) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void validateDropPayment(UUID dropId, UUID productId) {
        if (dropId == null) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_FAILED, "드롭 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_FAILED, "상품 ID는 필수입니다.");
        }
    }

    private void validateRafflePayment(
            UUID raffleId,
            UUID entryId,
            UUID productId,
            String billingKeyId
    ) {
        if (raffleId == null) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_FAILED, "래플 ID는 필수입니다.");
        }
        if (entryId == null) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_FAILED, "래플 응모 ID는 필수입니다.");
        }
        if (productId == null) {
            throw new NonRetryablePaymentException(PaymentErrorCode.PAYMENT_FAILED, "상품 ID는 필수입니다.");
        }
        if (billingKeyId == null || billingKeyId.isBlank()) {
            throw new NonRetryablePaymentException(
                    PaymentErrorCode.PAYMENT_FAILED,
                    "자동결제를 위한 billingKey가 없습니다."
            );
        }
    }

    // 쿠폰이 있으면 결제 직전에 선점하고 응답으로 상태와 할인 금액을 확인
    private void reserveAndValidateCoupon(
            UUID couponId,
            UUID orderId,
            UUID userId,
            Long originalAmount,
            Long discountAmount
    ) {
        long resolvedDiscountAmount = discountAmount == null ? 0L : discountAmount;

        if (couponId == null) {
            if (resolvedDiscountAmount != 0L) {
                throw new NonRetryablePaymentException(
                        PaymentErrorCode.PAYMENT_INVALID_COUPON,
                        "쿠폰 없이 할인 금액을 적용할 수 없습니다."
                );
            }
            return;
        }

        UserCouponResponse coupon = reserveCoupon(couponId, orderId, userId);
        if (!"RESERVED".equals(coupon.status())) {
            throw new NonRetryablePaymentException(
                    PaymentErrorCode.PAYMENT_INVALID_COUPON,
                    "쿠폰 상태가 RESERVED가 아닙니다."
            );
        }

        long expectedDiscountAmount = calculateCouponDiscountAmount(coupon, originalAmount);
        if (expectedDiscountAmount != resolvedDiscountAmount) {
            throw new NonRetryablePaymentException(
                    PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "쿠폰 할인 금액이 일치하지 않습니다."
            );
        }
    }

    private void failValidation(Payment payment, NonRetryablePaymentException exception) {
        payment.fail(exception.getErrorCode().getCode(), exception.getMessage());
        paymentOutboxService.savePaymentFailed(payment);
    }

    // coupon-service에서 쿠폰을 선점하고 응답을 결제 검증에 사용
    private UserCouponResponse reserveCoupon(UUID couponId, UUID orderId, UUID userId) {
        try {
            CouponReserveRequest request = new CouponReserveRequest(couponId, orderId, userId);
            ApiResponse<UserCouponResponse> response = couponServiceClient.reserveCoupon(request);
            if (response == null || response.getData() == null) {
                throw new BusinessException(CommonErrorCode.REMOTE_RESPONSE_PARSE_ERROR, "쿠폰 서비스 응답이 비어 있습니다.");
            }
            return response.getData();
        } catch (FeignException e) {
            throw new BusinessException(CommonErrorCode.REMOTE_CALL_FAILED, "쿠폰 서비스 호출에 실패했습니다.");
        }
    }

    // 쿠폰 타입에 맞춰 실제 할인 금액을 다시 계산
    private long calculateCouponDiscountAmount(UserCouponResponse coupon, Long originalAmount) {
        BigDecimal originalAmountValue = BigDecimal.valueOf(originalAmount);
        if (coupon.discountValue() == null) {
            throw new NonRetryablePaymentException(
                    PaymentErrorCode.PAYMENT_INVALID_COUPON,
                    "쿠폰 할인 값은 필수입니다."
            );
        }

        if ("AMOUNT".equals(coupon.discountType())) {
            return coupon.discountValue().setScale(0, RoundingMode.DOWN).longValue();
        }

        // 소수점 버림 정책 적용
        if ("RATE".equals(coupon.discountType())) {
            BigDecimal calculated = originalAmountValue
                    .multiply(coupon.discountValue())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);

            // 최대 할인 금액 적용
            if (coupon.maxDiscountAmount() != null) {
                BigDecimal maxDiscount = coupon.maxDiscountAmount().setScale(0, RoundingMode.DOWN);
                calculated = calculated.min(maxDiscount);
            }

            return calculated.longValue();
        }

        throw new NonRetryablePaymentException(
                PaymentErrorCode.PAYMENT_INVALID_COUPON,
                "지원하지 않는 쿠폰 할인 타입입니다."
        );
    }
}
