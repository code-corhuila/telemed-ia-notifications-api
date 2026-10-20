package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationStatus;
import com.telemed.notifications.domain.model.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Maps notifications.notification, owned by telemed-ia-notifications-db.
 *
 * <p>channel, created_at and updated_at are left to the database defaults
 * and trigger.
 */
@Entity
@Table(name = "notification", schema = "notifications")
public class NotificationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false, updatable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "source_event_id", nullable = false, updatable = false)
    private UUID sourceEventId;

    @Column(name = "correlation_id", updatable = false, length = 128)
    private String correlationId;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    protected NotificationJpaEntity() {
    }

    public static NotificationJpaEntity fromDomain(
            Notification notification
    ) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.id = notification.id();
        entity.recipientUserId = notification.recipientUserId();
        entity.type = notification.type();
        entity.status = notification.status();
        entity.attemptCount = notification.attemptCount();
        entity.lastError = notification.lastError();
        entity.sourceEventId = notification.sourceEventId();
        entity.correlationId = notification.correlationId();
        entity.sentAt = notification.sentAt() == null
                ? null
                : OffsetDateTime.ofInstant(notification.sentAt(), ZoneOffset.UTC);
        return entity;
    }

    public Notification toDomain() {
        return new Notification(
                id,
                recipientUserId,
                type,
                status,
                attemptCount,
                lastError,
                sourceEventId,
                correlationId,
                sentAt == null ? null : sentAt.toInstant()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public UUID getSourceEventId() {
        return sourceEventId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }
}
