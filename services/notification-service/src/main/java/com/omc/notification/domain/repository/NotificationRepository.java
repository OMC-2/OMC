package com.omc.notification.domain.repository;

import com.omc.notification.domain.entity.Notification;
import com.omc.notification.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Notification> findTop50ByStatusOrderByCreatedAtAsc(NotificationStatus status);

    List<Notification> findTop100ByStatusOrderByCreatedAtAsc(NotificationStatus status);
}
