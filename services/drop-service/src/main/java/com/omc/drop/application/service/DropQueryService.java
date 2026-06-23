package com.omc.drop.application.service;

import com.omc.common.response.PageResponse;
import com.omc.common.util.PageableUtil;
import com.omc.drop.domain.entity.Drop;
import com.omc.drop.domain.enums.DropStatus;
import com.omc.drop.domain.repository.DropRepository;
import com.omc.drop.presentation.dto.response.ActiveDropResponse;
import com.omc.drop.presentation.dto.response.DropResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DropQueryService {

    private final DropRepository dropRepository;

    public PageResponse<DropResponse> findAll(DropStatus status, Pageable pageable) {
        Pageable validated = PageableUtil.validatePageSize(pageable);
        return new PageResponse<>(fetchPage(status, validated).map(DropResponse::from));
    }

    public DropResponse findById(UUID dropId) {
        Drop drop = dropRepository.getByIdOrThrow(dropId);
        return DropResponse.from(drop);
    }

    public ActiveDropResponse hasActiveDrop(UUID productId) {
        boolean exists = dropRepository.existsByProductIdAndStatusIn(
                productId, List.of(DropStatus.SCHEDULED, DropStatus.OPEN));
        return ActiveDropResponse.of(exists);
    }

    private Page<Drop> fetchPage(DropStatus status, Pageable pageable) {
        return status != null
                ? dropRepository.findAllByStatus(status, pageable)
                : dropRepository.findAll(pageable);
    }
}
