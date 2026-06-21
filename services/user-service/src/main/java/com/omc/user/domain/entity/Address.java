package com.omc.user.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_addresses", schema = "user_db")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;

    @Column(name = "address", nullable = false, length = 200)
    private String address;

    @Column(name = "address_detail", length = 200)
    private String addressDetail;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Address(UUID userId, String recipientName, String phone, String zipCode,
                    String address, String addressDetail, boolean isDefault) {
        this.userId = userId;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.isDefault = isDefault;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public static Address create(UUID userId, String recipientName, String phone, String zipCode,
                                 String address, String addressDetail, boolean isDefault) {
        return Address.builder()
                .userId(userId)
                .recipientName(recipientName)
                .phone(phone)
                .zipCode(zipCode)
                .address(address)
                .addressDetail(addressDetail)
                .isDefault(isDefault)
                .build();
    }

    public void update(String recipientName, String phone, String zipCode, String address, String addressDetail) {
        if (recipientName != null) this.recipientName = recipientName;
        if (phone != null) this.phone = phone;
        if (zipCode != null) this.zipCode = zipCode;
        if (address != null) this.address = address;
        if (addressDetail != null) this.addressDetail = addressDetail;
    }

    public void markAsDefault() {
        this.isDefault = true;
    }

    public void unmarkAsDefault() {
        this.isDefault = false;
    }
}
