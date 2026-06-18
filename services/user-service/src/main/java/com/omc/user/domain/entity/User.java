package com.omc.user.domain.entity;

import com.omc.user.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_users", schema = "user_db")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "keycloak_id", nullable = false, unique = true, length = 100)
    private String keycloakId;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "slack_id", length = 100)
    private String slackId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public static User create(String keycloakId, String email, String nickname, String slackId) {
        User user = new User();
        user.keycloakId = keycloakId;
        user.email = email;
        user.nickname = nickname;
        user.slackId = slackId;
        user.role = UserRole.USER;
        return user;
    }

    public static User createAdmin(String keycloakId, String email, String nickname, String slackId) {
        User user = new User();
        user.keycloakId = keycloakId;
        user.email = email;
        user.nickname = nickname;
        user.slackId = slackId;
        user.role = UserRole.ADMIN;
        return user;
    }
}
