package com.omc.coupon.infrastructure.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class CouponLocalStore {

    private final ConcurrentHashMap<String, AtomicLong> stockMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> issuedMap = new ConcurrentHashMap<>();

    public void initCoupon(String couponId, long stock) {
        stockMap.put(couponId, new AtomicLong(stock));
        issuedMap.put(couponId, ConcurrentHashMap.newKeySet());
    }

    /**
     * 원자적 발급 시도.
     * 반환: -3=미초기화, -2=중복, -1=재고소진, 0이상=성공(차감 후 남은 재고)
     */
    public long tryIssue(String couponId, String userId) {
        AtomicLong stock = stockMap.get(couponId);
        Set<String> issued = issuedMap.get(couponId);
        if (stock == null || issued == null) return -3L;

        if (!issued.add(userId)) return -2L;

        long current;
        do {
            current = stock.get();
            if (current <= 0) {
                issued.remove(userId);
                return -1L;
            }
        } while (!stock.compareAndSet(current, current - 1));

        return current - 1;
    }

    public void rollback(String couponId, String userId) {
        AtomicLong stock = stockMap.get(couponId);
        if (stock != null) stock.incrementAndGet();
        Set<String> issued = issuedMap.get(couponId);
        if (issued != null) issued.remove(userId);
    }
}
