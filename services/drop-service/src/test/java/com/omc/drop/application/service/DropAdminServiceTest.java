package com.omc.drop.application.service;

import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.exception.DropNotFoundException;
import com.omc.drop.domain.exception.InvalidDropDateRangeException;
import com.omc.drop.domain.exception.InvalidDropStatusException;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.presentation.dto.request.DropCreateRequest;
import com.omc.drop.presentation.dto.request.DropUpdateRequest;
import com.omc.drop.presentation.dto.response.DropAdminResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.omc.common.security.CustomUserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DropAdminService 테스트")
class DropAdminServiceTest {

    @Mock
    private DropRepository dropRepository;

    @InjectMocks
    private DropAdminService dropAdminService;

    private final UUID adminId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        var auth = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(
                        adminId.toString(), "admin", "ADMIN",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                ),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("드롭 생성")
    class Create {

        @Test
        @DisplayName("드롭을 생성하고 저장한다")
        void createsDrop() {
            UUID productId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            LocalDateTime endAt = startAt.plusDays(2);
            DropCreateRequest request = new DropCreateRequest(productId, startAt, endAt, 100, 600);

            when(dropRepository.save(any(Drop.class))).thenAnswer(invocation -> {
                Drop drop = invocation.getArgument(0);
                ReflectionTestUtils.setField(drop, "dropId", UUID.randomUUID());
                return drop;
            });

            DropAdminResponse response = dropAdminService.create(request);

            assertThat(response.productId()).isEqualTo(productId);
            assertThat(response.status()).isEqualTo(DropStatus.SCHEDULED);
            assertThat(response.startAt()).isEqualTo(startAt);
            assertThat(response.endAt()).isEqualTo(endAt);
            assertThat(response.totalQty()).isEqualTo(100);
            assertThat(response.holdTtlSec()).isEqualTo(600);
            verify(dropRepository).save(any(Drop.class));
        }

        @Test
        @DisplayName("종료 시간이 시작 시간 이전이면 예외가 발생한다")
        void throwsWhenEndAtIsBeforeStartAt() {
            LocalDateTime startAt = LocalDateTime.now().plusDays(2);
            LocalDateTime endAt = startAt.minusDays(1);
            DropCreateRequest request = new DropCreateRequest(UUID.randomUUID(), startAt, endAt, 100, 600);

            assertThatThrownBy(() -> dropAdminService.create(request))
                    .isInstanceOf(InvalidDropDateRangeException.class);

            verify(dropRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("드롭 수정")
    class Update {

        @Test
        @DisplayName("SCHEDULED 상태의 드롭을 수정한다")
        void updatesDrop() {
            UUID dropId = UUID.randomUUID();
            LocalDateTime newStartAt = LocalDateTime.now().plusDays(3);
            LocalDateTime newEndAt = newStartAt.plusDays(2);
            DropUpdateRequest request = new DropUpdateRequest(newStartAt, newEndAt, 200, 300);

            Drop drop = createScheduledDrop(dropId);
            when(dropRepository.getByIdOrThrow(dropId)).thenReturn(drop);

            DropAdminResponse response = dropAdminService.update(dropId, request);

            assertThat(response.startAt()).isEqualTo(newStartAt);
            assertThat(response.endAt()).isEqualTo(newEndAt);
            assertThat(response.totalQty()).isEqualTo(200);
            assertThat(response.holdTtlSec()).isEqualTo(300);
            assertThat(response.status()).isEqualTo(DropStatus.SCHEDULED);
        }

        @Test
        @DisplayName("종료 시간이 시작 시간 이전이면 DB 조회 없이 예외가 발생한다")
        void throwsWhenEndAtIsBeforeStartAtWithoutDbQuery() {
            UUID dropId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusDays(2);
            LocalDateTime endAt = startAt.minusDays(1);
            DropUpdateRequest request = new DropUpdateRequest(startAt, endAt, 100, 600);

            assertThatThrownBy(() -> dropAdminService.update(dropId, request))
                    .isInstanceOf(InvalidDropDateRangeException.class);

            verify(dropRepository, never()).getByIdOrThrow(any());
        }

        @Test
        @DisplayName("존재하지 않는 드롭이면 예외가 발생한다")
        void throwsWhenDropNotFound() {
            UUID dropId = UUID.randomUUID();
            when(dropRepository.getByIdOrThrow(dropId)).thenThrow(new DropNotFoundException());

            assertThatThrownBy(() -> dropAdminService.update(dropId, validUpdateRequest()))
                    .isInstanceOf(DropNotFoundException.class);
        }

        @Test
        @DisplayName("SCHEDULED 상태가 아닌 드롭은 수정할 수 없다")
        void throwsWhenDropIsNotScheduled() {
            UUID dropId = UUID.randomUUID();
            Drop openDrop = createScheduledDrop(dropId);
            openDrop.open();
            when(dropRepository.getByIdOrThrow(dropId)).thenReturn(openDrop);

            assertThatThrownBy(() -> dropAdminService.update(dropId, validUpdateRequest()))
                    .isInstanceOf(InvalidDropStatusException.class);
        }
    }

    @Nested
    @DisplayName("드롭 삭제")
    class Delete {

        @Test
        @DisplayName("SCHEDULED 상태의 드롭을 소프트딜리트한다")
        void softDeletesDrop() {
            UUID dropId = UUID.randomUUID();
            Drop drop = createScheduledDrop(dropId);
            when(dropRepository.getByIdOrThrow(dropId)).thenReturn(drop);

            dropAdminService.delete(dropId);

            assertThat(drop.isDeleted()).isTrue();
            assertThat(drop.getDeletedBy()).isEqualTo(adminId);
            verify(dropRepository, never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 드롭이면 예외가 발생한다")
        void throwsWhenDropNotFound() {
            UUID dropId = UUID.randomUUID();
            when(dropRepository.getByIdOrThrow(dropId)).thenThrow(new DropNotFoundException());

            assertThatThrownBy(() -> dropAdminService.delete(dropId))
                    .isInstanceOf(DropNotFoundException.class);
        }

        @Test
        @DisplayName("SCHEDULED 상태가 아닌 드롭은 삭제할 수 없다")
        void throwsWhenDropIsNotScheduled() {
            UUID dropId = UUID.randomUUID();
            Drop openDrop = createScheduledDrop(dropId);
            openDrop.open();
            when(dropRepository.getByIdOrThrow(dropId)).thenReturn(openDrop);

            assertThatThrownBy(() -> dropAdminService.delete(dropId))
                    .isInstanceOf(InvalidDropStatusException.class);

            assertThat(openDrop.isDeleted()).isFalse();
        }
    }

    private Drop createScheduledDrop(UUID dropId) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        Drop drop = Drop.create(UUID.randomUUID(), startAt, startAt.plusDays(2), 100, 600);
        ReflectionTestUtils.setField(drop, "dropId", dropId);
        return drop;
    }

    private DropUpdateRequest validUpdateRequest() {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        return new DropUpdateRequest(startAt, startAt.plusDays(2), 100, 600);
    }
}
