package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.common.util.PageableUtil;
import com.omc.payment.domain.exception.PaymentGatewayConnectionException;
import com.omc.payment.domain.exception.PaymentGatewayRequestException;
import com.omc.payment.application.port.out.PaymentGatewayCommand;
import com.omc.payment.application.port.out.PaymentGatewayPort;
import com.omc.payment.application.port.out.PaymentGatewayResult;
import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.domain.enums.PaymentMethod;
import com.omc.payment.domain.enums.Provider;
import com.omc.payment.domain.enums.SalesType;
import com.omc.payment.domain.exception.PaymentErrorCode;
import com.omc.payment.domain.repository.PaymentRepository;
import com.omc.payment.presentation.dto.request.ConfirmBillingPaymentRequest;
import com.omc.payment.presentation.dto.request.ConfirmPaymentRequest;
import com.omc.payment.presentation.dto.request.PaymentCancelRequest;
import com.omc.payment.presentation.dto.request.RegisterBillingKeyRequest;
import com.omc.payment.presentation.dto.response.PaymentDetailResponse;
import com.omc.payment.presentation.dto.response.PaymentResponse;
import com.omc.payment.presentation.dto.response.RegisterBillingKeyResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayPort paymentGatewayPort;

    @Transactional
    public PaymentResponse confirmPayment(@Valid ConfirmPaymentRequest request) {
        validatePaymentAmounts(request.originalAmount(), request.discountAmount(), request.finalAmount());

        // 멱등성 방어 로직
        Payment existingPayment = paymentRepository.findByOrderId(request.orderID()).orElse(null);
        if (existingPayment != null) {
            return PaymentResponse.from(existingPayment);
        }

        Payment payment = paymentRepository.save(
                Payment.create(
                    request.orderID(),
                    null,
                    request.couponID(),
                    getCurrentUserId(),
                    SalesType.DROP,
                    request.originalAmount(),
                    request.discountAmount(),
                    Provider.TOSS,
                    PaymentMethod.CARD
                )
        );
        payment.startConfirming();

        // Mocking을 위한 랜덤 결제 식별자 Fallback
        String providerPaymentId = request.providerPaymentId() == null || request.providerPaymentId().isBlank()
                ? UUID.randomUUID().toString()
                : request.providerPaymentId();

        return confirmWithGateway(payment, request, providerPaymentId);
    }

    public RegisterBillingKeyResponse registerBillingKey(@Valid RegisterBillingKeyRequest request) {
        try {
            /*
            * Mocking을 위한 랜덤 키 Fallback
            * */
            String customerKey = request.customerKey() == null || request.customerKey().isBlank()
                    ? UUID.randomUUID().toString()
                    : request.customerKey();

            String authKey = request.authKey() == null || request.authKey().isBlank()
                    ? UUID.randomUUID().toString()
                    : request.authKey();

            PaymentGatewayResult.RegisterBillingKey result = paymentGatewayPort.registerBillingKey(
                    new PaymentGatewayCommand.RegisterBillingKey(customerKey, authKey)
            );
            return RegisterBillingKeyResponse.from(result.billingKeyID());
        } catch (PaymentGatewayRequestException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_REQUEST_FAILED, e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_CONNECTION_FAILED, e.getMessage());
        }
    }

    @Transactional
    public PaymentResponse confirmBillingPayment(@Valid ConfirmBillingPaymentRequest request) {
        validatePaymentAmounts(request.originalAmount(), request.discountAmount(), request.finalAmount());

        Payment existingPayment = paymentRepository.findByOrderId(request.orderId()).orElse(null);
        if (existingPayment != null) {
            return PaymentResponse.from(existingPayment);
        }

        Payment payment = paymentRepository.save(Payment.create(
                request.orderId(),
                request.entryId(),
                request.couponId(),
                request.userId(),
                SalesType.RAFFLE,
                request.originalAmount(),
                request.discountAmount(),
                Provider.TOSS,
                PaymentMethod.CARD
        ));

        payment.startConfirming();

        return confirmBillingWithGateway(payment, request);
    }

    @Transactional
    public PaymentResponse cancelPayment(UUID paymentId, @Valid PaymentCancelRequest request) {
        Payment payment = getPaymentEntity(paymentId);
        String role = getCurrentUserRole();

        switch (role) {
            case "ADMIN" -> {
                String providerCancellationId = cancelWithGateway(payment, request.cancelReason());
                payment.cancel(providerCancellationId, CancellationCode.ADMIN_CANCEL, request.cancelReason());

                return PaymentResponse.from(payment);
            }
            case "USER" -> {
                UUID currentUserId = getCurrentUserId();
                if (!currentUserId.equals(payment.getUserId())) {
                    throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
                }
                String providerCancellationId = cancelWithGateway(payment, request.cancelReason());
                payment.cancel(providerCancellationId, CancellationCode.USER_CANCEL, request.cancelReason());

                return PaymentResponse.from(payment);
            }
            default -> throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // confirmPayment PG 연동 로직 분리
    private PaymentResponse confirmWithGateway(
            Payment payment,
            ConfirmPaymentRequest request,
            String providerPaymentId
    ) {
        try {
            PaymentGatewayResult.Confirm result = paymentGatewayPort.confirmPayment(
                    new PaymentGatewayCommand.Confirm(
                            providerPaymentId,
                            request.orderID().toString(),
                            request.finalAmount()
                    )
            );
            payment.approve(result.providerPaymentId());
            return PaymentResponse.from(payment);
        } catch (PaymentGatewayRequestException e) {
            /* FAILED 처리 */
            payment.fail(e.getProviderCode(), e.getMessage());
            throw new BusinessException(PaymentErrorCode.PAYMENT_FAILED, e.getMessage());
        } catch (PaymentGatewayConnectionException e) {
            /* UNKNOWN 처리, 추후 재처리 필요 */
            payment.markUnknown();
            throw new BusinessException(PaymentErrorCode.PAYMENT_GATEWAY_CONNECTION_FAILED, e.getMessage());
        }
    }

    // confirmBillingPayment PG 연동 로직 분리
    private PaymentResponse confirmBillingWithGateway(
            Payment payment,
            ConfirmBillingPaymentRequest request
    ) {
        try {
            String customerKey = request.customerKey() == null || request.customerKey().isBlank()
                    ? UUID.randomUUID().toString()
                    : request.customerKey();

            PaymentGatewayResult.Confirm result = paymentGatewayPort.confirmBillingPayment(
                    new PaymentGatewayCommand.ConfirmBilling(
                            request.billingKeyId(),
                            customerKey,
                            request.orderId().toString(),
                            "래플 자동결제",
                            request.finalAmount()
                    )
            );
            payment.approve(result.providerPaymentId());
            return PaymentResponse.from(payment);
        } catch (PaymentGatewayRequestException e) {
            payment.fail(e.getProviderCode(), e.getMessage());
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

    public PageResponse<PaymentDetailResponse> getMyPayments(Pageable pageable) {
        Pageable validatedPageable = PageableUtil.validatePageSize(pageable);
        UUID currentUserId = getCurrentUserId();
        Page<PaymentDetailResponse> page = paymentRepository.findAllByUserId(currentUserId, validatedPageable)
                .map(PaymentDetailResponse::from);
        return new PageResponse<>(page);
    }

    public PageResponse<PaymentDetailResponse> getPayments(Pageable pageable) {
        Pageable validatedPageable = PageableUtil.validatePageSize(pageable);
        Page<PaymentDetailResponse> page = paymentRepository.findAll(validatedPageable)
                .map(PaymentDetailResponse::from);
        return new PageResponse<>(page);
    }

    // PG 연동 전 검증
    private void validatePaymentAmounts(Long originalAmount, Long discountAmount, Long finalAmount) {
        // 금액 검증
        if (originalAmount - discountAmount != finalAmount) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private Payment getPaymentEntity(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }

    private String getCurrentUserRole() {
        return SecurityUtil.getCurrentUserRole()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
