package com.omc.payment.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.payment.domain.exception.PaymentGatewayConnectionException;
import com.omc.payment.domain.exception.PaymentGatewayRequestException;
import com.omc.payment.application.port.out.PaymentGatewayCommand;
import com.omc.payment.application.port.out.PaymentGatewayPort;
import com.omc.payment.application.port.out.PaymentGatewayResult;
import com.omc.payment.domain.exception.PaymentErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@ConditionalOnProperty(name = "payment.pg.mode", havingValue = "toss", matchIfMissing = true)
@RequiredArgsConstructor
public class TossPaymentAdapter implements PaymentGatewayPort {

    private final RestClient tossPaymentRestClient;
    private final ObjectMapper objectMapper;

    // Toss 일반 결제
    @Override
    public PaymentGatewayResult.Confirm confirmPayment(PaymentGatewayCommand.Confirm command) {
        PaymentResponse response = post(
                "/v1/payments/confirm",
                new ConfirmRequest(command.providerPaymentId(), command.orderId(), command.amount()),
                PaymentResponse.class,
                command.idempotencyKey()
        );
        return new PaymentGatewayResult.Confirm(response.PaymentKey());
    }

    // Toss 빌링키 발급
    @Override
    public PaymentGatewayResult.RegisterBillingKey registerBillingKey(PaymentGatewayCommand.RegisterBillingKey command) {
        BillingKeyResponse response = post(
                "/v1/billing/authorizations/issue",
                new BillingKeyRequest(command.authKey(), command.customerKey()),
                BillingKeyResponse.class,
                null
        );
        return new PaymentGatewayResult.RegisterBillingKey(response.billingKey());
    }

    // Toss 빌링키 자동 결제
    @Override
    public PaymentGatewayResult.Confirm confirmBillingPayment(PaymentGatewayCommand.ConfirmBilling command) {
        PaymentResponse response = post(
                "/v1/billing/{billingKey}",
                new ConfirmBillingRequest(
                        command.customerKey(),
                        command.orderId(),
                        command.orderName(),
                        command.amount()
                ),
                PaymentResponse.class,
                command.idempotencyKey(),
                command.billingKeyId()
        );
        return new PaymentGatewayResult.Confirm(response.PaymentKey());
    }

    // Toss 결제 취소
    @Override
    public PaymentGatewayResult.Cancel cancelPayment(PaymentGatewayCommand.Cancel command) {
        PaymentResponse response = post(
                "/v1/payments/{paymentKey}/cancel",
                new CancelRequest(command.cancelReason(), command.amount()),
                PaymentResponse.class,
                command.idempotencyKey(),
                command.providerPaymentId()
        );
        // lastTransactionKey가 없을 경우 paymentKey 폴백
        String providerCancellationKey = response.lastTransactionKey() == null
                ? command.providerPaymentId()
                : response.lastTransactionKey();

        return new PaymentGatewayResult.Cancel(providerCancellationKey);
    }

    // Toss Post 호출 공통 로직
    private <T> T post(
            String uri,
            Object body,
            Class<T> responseType,
            String idempotencyKey,
            Object... uriVariables
    ) {
        try {
            RestClient.RequestBodySpec requestBodySpec = tossPaymentRestClient.post()
                    .uri(uri, uriVariables);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                requestBodySpec.header("Idempotency-Key", idempotencyKey);
            }

            return requestBodySpec
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            throw toBusinessException(e);
        } catch (RestClientException e) {
            log.error("Toss API 통신에 실패했습니다.", e);
            throw new PaymentGatewayConnectionException("Toss 결제 게이트웨이 통신에 실패했습니다.", e);
        }
    }

    // Toss 에러 응답을 애플리케이션 응답으로 변환
    private PaymentGatewayRequestException toBusinessException(RestClientResponseException e) {
        ErrorResponse error = readErrorResponse(e.getResponseBodyAsString());
        String providerCode = error == null || error.code() == null || error.code().isBlank()
                ? PaymentErrorCode.PAYMENT_GATEWAY_REQUEST_FAILED.getCode() :  error.code();
        String message = error == null || error.message() == null || error.message().isBlank()
                ? "Toss 결제 게이트웨이 요청에 실패했습니다."
                : error.message();
        log.warn("Toss API 요청에 실패했습니다. status={}, code={}, message={}",
                e.getStatusCode(), providerCode, message);
        return new PaymentGatewayRequestException(providerCode, message);
    }

    // Toss 에러 ErrorResponse 형태로 파싱
    private ErrorResponse readErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(responseBody, ErrorResponse.class);
        } catch (Exception e) {
            log.warn("Toss 에러 응답 본문 파싱에 실패했습니다. body={}", responseBody);
            return null;
        }
    }

    private record ConfirmRequest(
            String paymentKey,
            String orderId,
            Long amount
    ) {}

    private record BillingKeyRequest(
            String authKey,
            String customerKey
    ) {}

    private record ConfirmBillingRequest(
            String customerKey,
            String orderId,
            String orderName,
            Long amount
    ) {}

    private record CancelRequest(
            String cancelReason,
            Long cancelAmount
    ) {}

    // 승인, 취소 응답
    private record PaymentResponse(
            String PaymentKey, // 결제 식별키
            String lastTransactionKey // 마지막 거래의 키값
    ) {}

    private record BillingKeyResponse(
            String billingKey
    ) {}

    private record ErrorResponse(
            String code,
            String message
    ) {}
}
