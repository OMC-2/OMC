package com.omc.user.presentation.controller;

import com.omc.common.exception.BusinessException;
import com.omc.common.exception.CommonErrorCode;
import com.omc.common.response.ApiResponse;
import com.omc.common.response.PageResponse;
import com.omc.common.security.SecurityUtil;
import com.omc.user.application.service.AddressService;
import com.omc.user.presentation.dto.request.CreateAddressRequest;
import com.omc.user.presentation.dto.request.UpdateAddressRequest;
import com.omc.user.presentation.dto.response.AddressResponse;
import com.omc.user.presentation.dto.response.DefaultAddressResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @Valid @RequestBody CreateAddressRequest request) {
        UUID userId = resolveUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(addressService.createAddress(userId, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AddressResponse>>> getAddresses(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID userId = resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(addressService.getAddresses(userId, pageable)));
    }

    @GetMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(@PathVariable UUID addressId) {
        UUID userId = resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(addressService.getAddress(userId, addressId)));
    }

    @PatchMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable UUID addressId,
            @RequestBody UpdateAddressRequest request) {
        UUID userId = resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(addressService.updateAddress(userId, addressId, request)));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID addressId) {
        UUID userId = resolveUserId();
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<DefaultAddressResponse>> setDefaultAddress(@PathVariable UUID addressId) {
        UUID userId = resolveUserId();
        return ResponseEntity.ok(ApiResponse.success(addressService.setDefaultAddress(userId, addressId)));
    }

    private UUID resolveUserId() {
        return SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED));
    }
}
