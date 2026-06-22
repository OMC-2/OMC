package com.omc.user.unit.service;

import com.omc.common.response.PageResponse;
import com.omc.user.application.service.AddressService;
import com.omc.user.domain.entity.Address;
import com.omc.user.domain.exception.AddressNotFoundException;
import com.omc.user.domain.repository.AddressRepository;
import com.omc.user.presentation.dto.request.CreateAddressRequest;
import com.omc.user.presentation.dto.request.UpdateAddressRequest;
import com.omc.user.presentation.dto.response.AddressResponse;
import com.omc.user.presentation.dto.response.DefaultAddressResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    // =========================================================================
    // createAddress
    // =========================================================================

    @Test
    void createAddress_nonDefault_success() {
        UUID userId = UUID.randomUUID();
        CreateAddressRequest request = new CreateAddressRequest(
                "테스터", "010-1234-5678", "12345", "서울시 강남구", null, false);

        Address savedAddress = mock(Address.class);
        given(savedAddress.getAddressId()).willReturn(UUID.randomUUID());
        given(savedAddress.getRecipientName()).willReturn("테스터");
        given(addressRepository.save(any(Address.class))).willReturn(savedAddress);

        AddressResponse response = addressService.createAddress(userId, request);

        assertThat(response).isNotNull();
        verify(addressRepository, never()).unmarkAllDefaultByUserId(any());
        verify(addressRepository).save(any(Address.class));
    }

    @Test
    void createAddress_defaultTrue_unmarksExistingAddresses() {
        UUID userId = UUID.randomUUID();
        CreateAddressRequest request = new CreateAddressRequest(
                "테스터", "010-1234-5678", "12345", "서울시 강남구", null, true);

        Address savedAddress = mock(Address.class);
        given(savedAddress.getAddressId()).willReturn(UUID.randomUUID());
        given(savedAddress.isDefault()).willReturn(true);
        given(addressRepository.save(any(Address.class))).willReturn(savedAddress);

        AddressResponse response = addressService.createAddress(userId, request);

        verify(addressRepository).unmarkAllDefaultByUserId(userId);
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void createAddress_defaultNull_treatedAsFalse() {
        UUID userId = UUID.randomUUID();
        CreateAddressRequest request = new CreateAddressRequest(
                "테스터", "010-1234-5678", "12345", "서울시 강남구", null, null);

        Address savedAddress = mock(Address.class);
        given(savedAddress.getAddressId()).willReturn(UUID.randomUUID());
        given(addressRepository.save(any(Address.class))).willReturn(savedAddress);

        addressService.createAddress(userId, request);

        verify(addressRepository, never()).unmarkAllDefaultByUserId(any());
    }

    // =========================================================================
    // getAddresses
    // =========================================================================

    @Test
    void getAddresses_success_returnsPageResponse() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Address mockAddress = mock(Address.class);
        given(mockAddress.getAddressId()).willReturn(UUID.randomUUID());
        given(mockAddress.getRecipientName()).willReturn("테스터");
        given(mockAddress.getPhone()).willReturn("010-1234-5678");
        given(mockAddress.getZipCode()).willReturn("12345");
        given(mockAddress.getAddress()).willReturn("서울시 강남구");

        Page<Address> page = new PageImpl<>(List.of(mockAddress), pageable, 1);
        given(addressRepository.findByUserId(userId, pageable)).willReturn(page);

        PageResponse<AddressResponse> result = addressService.getAddresses(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(addressRepository).findByUserId(userId, pageable);
    }

    // =========================================================================
    // getAddress
    // =========================================================================

    @Test
    void getAddress_success_returnsAddressResponse() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        Address mockAddress = mock(Address.class);
        given(mockAddress.getAddressId()).willReturn(addressId);
        given(mockAddress.getRecipientName()).willReturn("테스터");
        given(mockAddress.getPhone()).willReturn("010-1234-5678");
        given(mockAddress.getZipCode()).willReturn("12345");
        given(mockAddress.getAddress()).willReturn("서울시 강남구");
        given(addressRepository.findByAddressIdAndUserId(addressId, userId)).willReturn(Optional.of(mockAddress));

        AddressResponse response = addressService.getAddress(userId, addressId);

        assertThat(response.addressId()).isEqualTo(addressId);
        assertThat(response.recipientName()).isEqualTo("테스터");
    }

    @Test
    void getAddress_notFound_throwsAddressNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        given(addressRepository.findByAddressIdAndUserId(addressId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress(userId, addressId))
                .isInstanceOf(AddressNotFoundException.class);
    }

    // =========================================================================
    // updateAddress
    // =========================================================================

    @Test
    void updateAddress_success_updatesFields() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UpdateAddressRequest request = new UpdateAddressRequest("수정된이름", "010-9999-9999", "99999", "서울시 서초구", null);

        Address mockAddress = mock(Address.class);
        given(mockAddress.getAddressId()).willReturn(addressId);
        given(mockAddress.getRecipientName()).willReturn("수정된이름");
        given(mockAddress.getPhone()).willReturn("010-9999-9999");
        given(mockAddress.getZipCode()).willReturn("99999");
        given(mockAddress.getAddress()).willReturn("서울시 서초구");
        given(addressRepository.findByAddressIdAndUserId(addressId, userId)).willReturn(Optional.of(mockAddress));

        AddressResponse response = addressService.updateAddress(userId, addressId, request);

        verify(mockAddress).update("수정된이름", "010-9999-9999", "99999", "서울시 서초구", null);
        assertThat(response.recipientName()).isEqualTo("수정된이름");
    }

    @Test
    void updateAddress_notFound_throwsAddressNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        given(addressRepository.findByAddressIdAndUserId(addressId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.updateAddress(
                userId, addressId, new UpdateAddressRequest(null, null, null, null, null)))
                .isInstanceOf(AddressNotFoundException.class);
    }

    // =========================================================================
    // deleteAddress
    // =========================================================================

    @Test
    void deleteAddress_success_callsRepositoryDelete() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();

        Address mockAddress = mock(Address.class);
        given(addressRepository.findByAddressIdAndUserId(addressId, userId)).willReturn(Optional.of(mockAddress));

        addressService.deleteAddress(userId, addressId);

        verify(addressRepository).delete(mockAddress);
    }

    @Test
    void deleteAddress_notFound_throwsAddressNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        given(addressRepository.findByAddressIdAndUserId(addressId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(userId, addressId))
                .isInstanceOf(AddressNotFoundException.class);

        verify(addressRepository, never()).delete(any());
    }

    // =========================================================================
    // setDefaultAddress
    // =========================================================================

    @Test
    void setDefaultAddress_success_marksTargetAndUnmarksOthers() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Address targetAddress = mock(Address.class);
        given(targetAddress.getAddressId()).willReturn(targetId);
        given(addressRepository.findByAddressIdAndUserId(targetId, userId)).willReturn(Optional.of(targetAddress));

        DefaultAddressResponse response = addressService.setDefaultAddress(userId, targetId);

        verify(addressRepository).unmarkAllDefaultByUserId(userId);
        verify(targetAddress).markAsDefault();
        assertThat(response.addressId()).isEqualTo(targetId);
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void setDefaultAddress_notFound_throwsAddressNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        given(addressRepository.findByAddressIdAndUserId(targetId, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.setDefaultAddress(userId, targetId))
                .isInstanceOf(AddressNotFoundException.class);
    }
}
