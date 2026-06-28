package com.omc.product.application.service;

import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.exception.InventoryNotFoundException;
import com.omc.product.domain.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 재고 확정 차감 전용 컴포넌트
 *
 * REQUIRES_NEW 트랜잭션 적용 이유:
 * ObjectOptimisticLockingFailureException은 트랜잭션 커밋 시점에 발생
 * 이 컴포넌트를 REQUIRES_NEW로 분리하면 메서드 종료 시 즉시 커밋이 발생하므로
 * 호출부(InventoryService)의 try-catch 블록에서 예외를 잡을 수 있음
 *
 * self-invocation 방지:
 * InventoryService 내부에서 직접 호출하면 Spring AOP 프록시를 우회하여
 * REQUIRES_NEW가 동작하지 않으므로 별도 컴포넌트로 분리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryDeductProcessor {

    private final InventoryRepository inventoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID tryDeduct(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElseThrow(InventoryNotFoundException::new);
        inventory.confirmDeduct(1);
        // 메서드 종료 시 REQUIRES_NEW 트랜잭션 커밋
        // → ObjectOptimisticLockingFailureException 발생 시 호출부로 전파
        return inventory.getInventoryId();
    }
}
