package com.telemed.notifications.application.port.out;

import com.telemed.notifications.domain.model.Notification;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {

    Optional<Notification> findBySourceEventId(
            UUID sourceEventId
    );

    Notification save(
            Notification notification
    );
}
