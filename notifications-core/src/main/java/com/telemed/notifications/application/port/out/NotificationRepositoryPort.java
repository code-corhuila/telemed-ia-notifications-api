package com.telemed.notifications.application.port.out;

import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationType;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepositoryPort {

    /** One event may notify several recipients, each with its own record. */
    Optional<Notification> findBySourceEventIdAndRecipient(
            UUID sourceEventId,
            UUID recipientUserId
    );

    Notification save(
            Notification notification
    );

    /** Whether the recipient was already notified of this type about that subject. */
    boolean existsByRecipientTypeAndSubject(
            UUID recipientUserId,
            NotificationType type,
            UUID subjectId
    );
}
