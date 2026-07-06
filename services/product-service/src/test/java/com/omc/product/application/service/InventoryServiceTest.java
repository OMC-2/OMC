package com.omc.product.application.service;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.application.processor.InventoryDeductProcessor;
import com.omc.product.application.processor.StockFailureHandler;
import com.omc.product.application.processor.StockSuccessHandler;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.exception.InsufficientStockException;
import com.omc.product.domain.repository.InventoryRepository;
import com.omc.product.domain.repository.ProcessedEventRepository;
import com.omc.product.infrastructure.client.ActiveDropResponse;
import com.omc.product.infrastructure.client.DropFeignClient;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private DropFeignClient dropFeignClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private InventoryDeductProcessor inventoryDeductProcessor;
    @Mock private StockSuccessHandler stockSuccessHandler;
    @Mock private StockFailureHandler stockFailureHandler;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID productId;
    private UUID orderId;
    private UUID inventoryId;
    private PaymentCompletedEvent event;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        inventoryId = UUID.randomUUID();
        event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(), orderId, productId,
                UUID.randomUUID(), UUID.randomUUID(), 648000L
        );
    }

    @Test
    @DisplayName("재고 확정 차감에 성공하면 판매 수량을 증가시키고 Outbox 이벤트를 저장한다")
    void confirmDeduct_success() {
        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(false);
        given(inventoryDeductProcessor.tryDeduct(productId)).willReturn(inventoryId);

        inventoryService.confirmDeduct(event);

        verify(inventoryDeductProcessor, times(1)).tryDeduct(productId);
        verify(stockSuccessHandler, times(1)).handle(inventoryId, event);
        verify(stockFailureHandler, never()).handle(any(), any(), any());
    }

    @Test
    @DisplayName("이미 처리한 이벤트를 재수신하면 추가 처리하지 않는다")
    void confirmDeduct_idempotent_skip() {
        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(true);

        inventoryService.confirmDeduct(event);

        verify(inventoryDeductProcessor, never()).tryDeduct(any());
        verify(stockSuccessHandler, never()).handle(any(), any());
        verify(stockFailureHandler, never()).handle(any(), any(), any());
    }

    @Test
    @DisplayName("낙관적 락 충돌이 발생하면 실패 이벤트를 기록하고 stock.failed 이벤트를 저장한다")
    void confirmDeduct_optimisticLockFailure() {
        Inventory inventory = mock(Inventory.class);
        given(inventory.getInventoryId()).willReturn(inventoryId);
        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(false);
        given(inventoryDeductProcessor.tryDeduct(productId))
                .willThrow(new ObjectOptimisticLockingFailureException(Inventory.class, inventoryId));
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        inventoryService.confirmDeduct(event);

        verify(stockFailureHandler, times(1)).handle(eq(inventoryId), eq(event), any());
        verify(stockSuccessHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("재고 부족 시 stock.failed 이벤트를 저장하고 실패 로그를 기록한다")
    void confirmDeduct_insufficientStock() {
        Inventory inventory = mock(Inventory.class);
        given(inventory.getInventoryId()).willReturn(inventoryId);
        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(false);
        given(inventoryDeductProcessor.tryDeduct(productId))
                .willThrow(new InsufficientStockException());
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));

        inventoryService.confirmDeduct(event);

        verify(stockFailureHandler, times(1)).handle(eq(inventoryId), eq(event), any());
        verify(stockSuccessHandler, never()).handle(any(), any());
    }

    @Test
    @DisplayName("진행 중인 Drop이 있으면 재고 수정 시 ActiveDropExistsException 발생시킨다")
    void updateInventory_activeDropExists() {
        given(dropFeignClient.hasActiveDrop(any()))
                .willReturn(ApiResponse.success(new ActiveDropResponse(true)));

        assertThatThrownBy(() -> inventoryService.updateInventory(
                productId, new InventoryUpdateRequest(15, "입고 추가")))
                .isInstanceOf(ActiveDropExistsException.class);
    }
}
