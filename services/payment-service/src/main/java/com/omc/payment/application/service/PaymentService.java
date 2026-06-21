package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.common.util.PageableUtil;
import com.omc.payment.application.exception.PaymentGatewayConnectionException;
import com.omc.payment.application.exception.PaymentGatewayRequestException;
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
        validatePayment(request);

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

        try {
            // Mocking을 위한 랜덤 결제 식별자 생성
            UUID randomProviderPaymentId = UUID.randomUUID();
            PaymentGatewayResult.Confirm result = paymentGatewayPort.confirmPayment(
                    new PaymentGatewayCommand.Confirm(
                            randomProviderPaymentId.toString(),
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

    public RegisterBillingKeyResponse registerBillingKey(@Valid RegisterBillingKeyRequest request) {
        try {
            String customerKey = UUID.randomUUID().toString();
            String authKey = UUID.randomUUID().toString();

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
    public PaymentResponse cancelPayment(UUID paymentId, @Valid PaymentCancelRequest request) {
        Payment payment = getPaymentEntity(paymentId);
        String role = getCurrentUserRole();

        switch (role) {
            case "ADMIN" -> {
                payment.cancel(CancellationCode.ADMIN_CANCEL, request.cancelReason());
                /*
                 * TODO PG 취소 연동 구현
                 */
                return PaymentResponse.from(payment);
            }
            case "USER" -> {
                UUID currentUserId = getCurrentUserId();
                if (!currentUserId.equals(payment.getUserId())) {
                    throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
                }

                payment.cancel(CancellationCode.USER_CANCEL, request.cancelReason());
                /*
                 * TODO PG 취소 연동 구현
                 */
                return PaymentResponse.from(payment);
            }
            default -> throw new BusinessException(CommonErrorCode.ACCESS_DENIED);
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
    private void validatePayment(ConfirmPaymentRequest request) {
        // 금액 검증
        if (request.originalAmount() - request.discountAmount() != request.finalAmount()) {
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
