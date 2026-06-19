package com.omc.payment.application.service;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.common.util.PageableUtil;
import com.omc.payment.domain.entity.Payment;
import com.omc.payment.domain.enums.CancellationCode;
import com.omc.payment.domain.exception.PaymentErrorCode;
import com.omc.payment.domain.repository.PaymentRepository;
import com.omc.payment.presentation.dto.request.ConfirmPaymentRequest;
import com.omc.payment.presentation.dto.request.PaymentCancelRequest;
import com.omc.payment.presentation.dto.request.RegisterBillingKeyRequest;
import com.omc.payment.presentation.dto.response.PaymentDetailResponse;
import com.omc.payment.presentation.dto.response.PaymentResponse;
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

                payment.refund(CancellationCode.USER_CANCEL, request.cancelReason());
                /*
                 * TODO PG 환불 연동 구현
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

    public void confirmPayment(@Valid ConfirmPaymentRequest request) {
        /*
         * TODO PG 결제 승인 구현
         */
    }

    public void registerBillingKey(@Valid RegisterBillingKeyRequest request) {
        /*
         * TODO PG 빌링키 등록 구현
         */
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
