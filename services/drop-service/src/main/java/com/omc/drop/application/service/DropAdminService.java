package com.omc.drop.application.service;

import com.omc.common.exception.UnauthorizedException;
import com.omc.common.security.SecurityUtil;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.presentation.dto.request.DropCreateRequest;
import com.omc.drop.presentation.dto.request.DropUpdateRequest;
import com.omc.drop.presentation.dto.response.DropAdminResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DropAdminService {

    private final DropRepository dropRepository;

    @Transactional
    public DropAdminResponse create(DropCreateRequest request) {
        Drop drop = createDrop(request);
        return DropAdminResponse.from(dropRepository.save(drop));
    }

    @Transactional
    public DropAdminResponse update(UUID dropId, DropUpdateRequest request) {
        Drop.validateDateRange(request.startAt(), request.endAt()); // fast-fail: 날짜 오류 시 DB 조회 생략 (최종 검증은 엔티티)

        Drop drop = dropRepository.getByIdOrThrow(dropId);
        drop.update(request.startAt(), request.endAt(), request.totalQty(), request.holdTtlSec());
        return DropAdminResponse.from(drop);
    }

    @Transactional
    public void delete(UUID dropId) {
        UUID deletedBy = SecurityUtil.getCurrentUserId()
                .orElseThrow(UnauthorizedException::new);
        Drop drop = dropRepository.getByIdOrThrow(dropId);
        drop.delete(deletedBy);
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
