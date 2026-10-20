package com.telemed.notifications.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataNotificationRepository
        extends JpaRepository<NotificationJpaEntity, UUID> {

    Optional<NotificationJpaEntity> findBySourceEventId(
            UUID sourceEventId
    );
}
