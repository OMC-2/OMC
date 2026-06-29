package com.omc.raffle.infrastructure.client;

import com.omc.raffle.domain.exception.PaymentPreAuthFailedException;
import com.omc.raffle.domain.exception.RaffleErrorCode;
import com.omc.raffle.infrastructure.client.dto.PreAuthRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentFeignClientFallback implements PaymentFeignClient {

    @Override
    public void preAuthCard(PreAuthRequest request) {
        log.error("[PaymentFeignClientFallback] 결제 서비스 통신 실패 또는 서킷 브레이커 오픈. billingKeyId={}", request.billingKeyId());
        throw new PaymentPreAuthFailedException(RaffleErrorCode.RAFFLE_004, "서킷 브레이커: 결제 시스템 장애로 인해 요청이 차단되었습니다.");
    }
}
