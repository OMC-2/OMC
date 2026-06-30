package com.omc.drop.application.service;

import com.omc.common.exception.UnauthorizedException;
import com.omc.common.security.SecurityUtil;
import com.omc.drop.application.event.producer.DropClosedEvent;
import com.omc.drop.application.event.producer.DropEventProducer;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.infrastructure.redis.DropRedisStore;
import com.omc.drop.presentation.dto.request.DropCreateRequest;
import com.omc.drop.presentation.dto.request.DropUpdateRequest;
import com.omc.drop.presentation.dto.response.DropAdminResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DropAdminService {

    private final DropRepository dropRepository;
    private final DropRedisStore dropRedisStore;
    private final DropEventProducer dropEventProducer;

    public Page<DropAdminResponse> getAll(Pageable pageable) {
        return dropRepository.findAllIncludingDeleted(pageable).map(DropAdminResponse::from);
    }

    public DropAdminResponse getOne(UUID dropId) {
        return DropAdminResponse.from(dropRepository.getByIdIncludingDeletedOrThrow(dropId));
    }

    @Transactional
    public DropAdminResponse create(DropCreateRequest request) {
        Drop drop = createDrop(request);
        DropAdminResponse response = DropAdminResponse.from(dropRepository.save(drop));
        log.info("드롭 생성 완료: dropId={}, productId={}, startAt={}, endAt={}",
                response.dropId(), response.productId(), response.startAt(), response.endAt());
        return response;
    }

    @Transactional
    public DropAdminResponse update(UUID dropId, DropUpdateRequest request) {
        Drop.validateDateRange(request.startAt(), request.endAt()); // fast-fail: 날짜 오류 시 DB 조회 생략 (최종 검증은 엔티티)

        Drop drop = dropRepository.getByIdOrThrow(dropId);
        drop.update(request.startAt(), request.endAt(), request.totalQty(), request.holdTtlSec());
        log.info("드롭 수정 완료: dropId={}, startAt={}, endAt={}, totalQty={}",
                dropId, request.startAt(), request.endAt(), request.totalQty());
        return DropAdminResponse.from(drop);
    }

    @Transactional
    public void close(UUID dropId) {
        Drop drop = dropRepository.getByIdOrThrow(dropId);
        drop.validateOpen();
        int updated = dropRepository.updateStatusConditionally(dropId, DropStatus.OPEN, DropStatus.CLOSED);
        if (updated == 1) {
            dropRedisStore.deleteStatus(dropId);
            dropEventProducer.publishDropClosed(DropClosedEvent.from(drop));
            log.info("드롭 강제 종료 완료: dropId={}", dropId);
        }
    }

    @Transactional
    public void delete(UUID dropId) {
        // @PreAuthorize("hasRole('ADMIN')")가 인증을 보장하므로 정상 흐름에서는 도달하지 않음
        UUID deletedBy = SecurityUtil.getCurrentUserId()
                .orElseThrow(UnauthorizedException::new);
        Drop drop = dropRepository.getByIdOrThrow(dropId);
        drop.delete(deletedBy);
        log.info("드롭 삭제 완료: dropId={}, deletedBy={}", dropId, deletedBy);
    }

    private Drop createDrop(DropCreateRequest request) {
        return Drop.create(
                request.productId(),
                request.startAt(),
                request.endAt(),
                request.totalQty(),
                request.holdTtlSec()
        );
    }
}
