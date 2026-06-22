package com.omc.drop.application.service;

import com.omc.common.response.PageResponse;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.presentation.dto.response.ActiveDropResponse;
import com.omc.drop.presentation.dto.response.DropResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropQueryService 테스트")
class DropQueryServiceTest {

    @Mock
    private DropRepository dropRepository;

    @InjectMocks
    private DropQueryService dropQueryService;

    @Nested
    @DisplayName("드롭 목록 조회")
    class FindAll {

        @Test
        @DisplayName("status 없이 전체 드롭 목록을 반환한다")
        void returnsAllDropsWhenStatusIsNull() {
            Pageable pageable = PageRequest.of(0, 10);
            Drop scheduledDrop = createDrop(UUID.randomUUID(), DropStatus.SCHEDULED);
            Drop openDrop = createDrop(UUID.randomUUID(), DropStatus.OPEN);
            Page<Drop> page = new PageImpl<>(List.of(scheduledDrop, openDrop), pageable, 2);

            when(dropRepository.findAll(any(Pageable.class))).thenReturn(page);

            PageResponse<DropResponse> response = dropQueryService.findAll(null, pageable);

            assertThat(response.getContent()).hasSize(2);
            assertThat(response.getTotalElements()).isEqualTo(2);
            verify(dropRepository).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("status 필터로 드롭 목록을 반환한다")
        void returnsFilteredDropsByStatus() {
            Pageable pageable = PageRequest.of(0, 10);
            Drop openDrop = createDrop(UUID.randomUUID(), DropStatus.OPEN);
            Page<Drop> page = new PageImpl<>(List.of(openDrop), pageable, 1);

            when(dropRepository.findAllByStatus(eq(DropStatus.OPEN), any(Pageable.class))).thenReturn(page);

            PageResponse<DropResponse> response = dropQueryService.findAll(DropStatus.OPEN, pageable);

            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).status()).isEqualTo(DropStatus.OPEN);
            verify(dropRepository).findAllByStatus(eq(DropStatus.OPEN), any(Pageable.class));
        }

        @Test
        @DisplayName("목록이 비어 있으면 빈 페이지를 반환한다")
        void returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Drop> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(dropRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

            PageResponse<DropResponse> response = dropQueryService.findAll(null, pageable);

            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("드롭 단건 조회")
    class FindById {

        @Test
        @DisplayName("드롭 상세를 반환한다")
        void returnsDropDetail() {
            UUID dropId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = startAt.plusDays(2);

            Drop drop = Drop.create(productId, startAt, endAt, 50, 300);
            ReflectionTestUtils.setField(drop, "dropId", dropId);
            when(dropRepository.getByIdOrThrow(dropId)).thenReturn(drop);

            DropResponse response = dropQueryService.findById(dropId);

            assertThat(response.dropId()).isEqualTo(dropId);
            assertThat(response.productId()).isEqualTo(productId);
            assertThat(response.status()).isEqualTo(DropStatus.SCHEDULED);
            assertThat(response.startAt()).isEqualTo(startAt);
            assertThat(response.endAt()).isEqualTo(endAt);
            assertThat(response.totalQty()).isEqualTo(50);
        }

        @Test
        @DisplayName("존재하지 않는 드롭이면 예외가 발생한다")
        void throwsWhenDropNotFound() {
            UUID dropId = UUID.randomUUID();
            when(dropRepository.getByIdOrThrow(dropId)).thenThrow(new DropNotFoundException());

            assertThatThrownBy(() -> dropQueryService.findById(dropId))
                    .isInstanceOf(DropNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("활성 드롭 존재 여부 조회")
    class HasActiveDrop {

        @Test
        @DisplayName("SCHEDULED 또는 OPEN 드롭이 있으면 hasActiveDrop=true를 반환한다")
        void returnsTrueWhenActiveDropExists() {
            UUID productId = UUID.randomUUID();
            when(dropRepository.existsByProductIdAndStatusIn(productId, List.of(DropStatus.SCHEDULED, DropStatus.OPEN)))
                    .thenReturn(true);

            ActiveDropResponse response = dropQueryService.hasActiveDrop(productId);

            assertThat(response.hasActiveDrop()).isTrue();
        }

        @Test
        @DisplayName("활성 드롭이 없으면 hasActiveDrop=false를 반환한다")
        void returnsFalseWhenNoActiveDrop() {
            UUID productId = UUID.randomUUID();
            when(dropRepository.existsByProductIdAndStatusIn(productId, List.of(DropStatus.SCHEDULED, DropStatus.OPEN)))
                    .thenReturn(false);

            ActiveDropResponse response = dropQueryService.hasActiveDrop(productId);

            assertThat(response.hasActiveDrop()).isFalse();
        }
    }

    private Drop createDrop(UUID dropId, DropStatus status) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusDays(2), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", dropId);
        if (status == DropStatus.OPEN) {
            drop.open();
        } else if (status == DropStatus.CLOSED) {
            drop.open();
            drop.close();
        }
        return drop;
    }
}
