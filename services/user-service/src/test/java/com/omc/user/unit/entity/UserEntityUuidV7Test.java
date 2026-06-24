package com.omc.user.unit.entity;

import com.omc.user.domain.entity.Address;
import com.omc.user.domain.entity.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityUuidV7Test {

    // =========================================================================
    // [1] User.create() → userId version 7
    // =========================================================================

    @Test
    void create_userId_isVersion7() {
        User user = User.create("kc-001", "a@test.com", "nick", "U001");

        assertThat(user.getUserId().version()).isEqualTo(7);
    }

    // =========================================================================
    // [2] User.createAdmin() → userId version 7
    // =========================================================================

    @Test
    void createAdmin_userId_isVersion7() {
        User admin = User.createAdmin("kc-002", "admin@test.com", "admin", null);

        assertThat(admin.getUserId().version()).isEqualTo(7);
    }

    // =========================================================================
    // [3] Address.create() → addressId version 7
    // =========================================================================

    @Test
    void create_addressId_isVersion7() {
        Address address = Address.create(UUID.randomUUID(), "홍길동", "010-1234-5678",
                "12345", "서울시 강남구", "101호", false);

        assertThat(address.getAddressId().version()).isEqualTo(7);
    }

    // =========================================================================
    // [4] 연속 생성한 User ID가 시간순으로 정렬되는지 확인
    // =========================================================================

    @Test
    void create_multipleUsers_idsAreSortedByCreationOrder() throws InterruptedException {
        User first = User.create("kc-003", "b@test.com", "nick1", null);
        Thread.sleep(1);
        User second = User.create("kc-004", "c@test.com", "nick2", null);

        assertThat(first.getUserId().toString()).isLessThan(second.getUserId().toString());
    }

    // =========================================================================
    // [5] 연속 생성한 Address ID가 시간순으로 정렬되는지 확인
    // =========================================================================

    @Test
    void create_multipleAddresses_idsAreSortedByCreationOrder() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        Address first = Address.create(userId, "홍길동", "010-0000-0001", "11111", "addr1", null, false);
        Thread.sleep(1);
        Address second = Address.create(userId, "홍길동", "010-0000-0002", "22222", "addr2", null, false);

        assertThat(first.getAddressId().toString()).isLessThan(second.getAddressId().toString());
    }
}
