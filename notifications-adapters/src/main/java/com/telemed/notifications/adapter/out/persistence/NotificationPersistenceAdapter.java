package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.domain.model.Notification;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// @Component, not @Repository: exception translation would turn the
// IllegalStateException of this port back into a Spring DataAccessException.
@Component
public class NotificationPersistenceAdapter
        implements NotificationRepositoryPort {

    private final SpringDataNotificationRepository notifications;

    public NotificationPersistenceAdapter(
            SpringDataNotificationRepository notifications
    ) {
        this.notifications = notifications;
    }

    @Override
    public Optional<Notification> findBySourceEventId(
            UUID sourceEventId
    ) {
        try {
            return notifications.findBySourceEventId(sourceEventId)
                    .map(NotificationJpaEntity::toDomain);

        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Notification could not be retrieved.",
                    exception
            );
        }
    }

    @Override
    public Notification save(
            Notification notification
    ) {
        try {
            // Flushing here surfaces a duplicate source_event_id before the
            // caller sends the e-mail.
            return notifications.saveAndFlush(
                    NotificationJpaEntity.fromDomain(notification)
            ).toDomain();

        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Notification could not be persisted.",
                    exception
            );
        }
    }
}
