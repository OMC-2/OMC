package com.omc.product.application.service;

import com.omc.common.response.ApiResponse;
import com.omc.product.application.event.PaymentCompletedEvent;
import com.omc.product.domain.enums.OutboxEventType;
import com.omc.product.domain.entity.FailedEventLog;
import com.omc.product.domain.entity.Inventory;
import com.omc.product.domain.entity.OutboxEvent;
import com.omc.product.domain.entity.ProcessedEvent;
import com.omc.product.domain.exception.ActiveDropExistsException;
import com.omc.product.domain.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omc.product.infrastructure.client.DropInternalClient;
import com.omc.product.infrastructure.client.ActiveDropResponse;
import com.omc.product.presentation.dto.request.InventoryUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private FailedEventLogRepository failedEventLogRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private DropInternalClient dropInternalClient;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID productId;
    private UUID orderId;
    private Inventory inventory;
    private PaymentCompletedEvent event;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        inventory = mock(Inventory.class);
        event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(), orderId, productId,
                UUID.randomUUID(), UUID.randomUUID(), 1, 648000L
        );
    }

    @Test
    @DisplayName("재고 확정 차감에 성공하면 판매 수량을 증가시키고 Outbox 이벤트를 저장한다")
    void confirmDeduct_success() throws Exception {
        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(false);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        inventoryService.confirmDeduct(event);

        verify(inventory, times(1)).confirmDeduct(event.quantity());
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        verify(failedEventLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 처리한 이벤트를 재수신하면 추가 처리하지 않는다")
    void confirmDeduct_idempotent_skip() {

        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(true);

        inventoryService.confirmDeduct(event);

        verify(inventoryRepository, never()).findByProductId(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("낙관적 락 충돌이 발생하면 실패 이벤트를 기록하고 stock.failed 이벤트를 저장한다")
    void confirmDeduct_optimisticLockFailure() throws Exception {

        given(inventory.getInventoryId()).willReturn(UUID.randomUUID());
        given(processedEventRepository.existsByEventId(event.eventId())).willReturn(false);
        given(inventoryRepository.findByProductId(productId)).willReturn(Optional.of(inventory));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        doThrow(new ObjectOptimisticLockingFailureException(Inventory.class, inventory.getInventoryId()))
                .when(inventory).confirmDeduct(anyInt());

        inventoryService.confirmDeduct(event);

        verify(failedEventLogRepository, times(1)).save(any(FailedEventLog.class));
        verify(outboxEventRepository, times(1)).save(argThat(outbox ->
                OutboxEventType.STOCK_FAILED.equals(outbox.getEventType())
        ));
    }

    @Test
    @DisplayName("진행 중인 Drop이 있으면 재고 수정 시 ActiveDropExistsException 발생시킨다")
    void updateInventory_activeDropExists() {

        given(dropInternalClient.hasActiveDrop(any()))
                .willReturn(ApiResponse.success(new ActiveDropResponse(true)));

        assertThatThrownBy(() -> inventoryService.updateInventory(
                productId, new InventoryUpdateRequest(15, "입고 추가")))
                .isInstanceOf(ActiveDropExistsException.class);
    }
}