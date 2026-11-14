package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationType;
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
    public Optional<Notification> findBySourceEventIdAndRecipient(
            UUID sourceEventId,
            UUID recipientUserId
    ) {
        try {
            return notifications.findBySourceEventIdAndRecipientUserId(sourceEventId, recipientUserId)
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

    @Override
    public boolean existsByRecipientTypeAndSubject(
            UUID recipientUserId,
            NotificationType type,
            UUID subjectId
    ) {
        try {
            return notifications.existsByRecipientUserIdAndTypeAndSubjectId(recipientUserId, type, subjectId);

        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Notification could not be retrieved.",
                    exception
            );
        }
    }
}
