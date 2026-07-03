package com.omc.product.application.event.consumer;

import com.omc.product.application.event.producer.ProductUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ProductCacheEventHandler {

    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductUpdated(ProductUpdatedEvent event) {
        Cache cache = cacheManager.getCache("product");
        if (cache != null) {
            cache.evict(event.productId());
        }
    }
}