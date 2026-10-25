package com.telemed.notifications.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery record of one consumed event.
 *
 * <p>It never holds the recipient address, the message body or the reset
 * link: those travel in the event and are used only while sending.
 */
public record Notification(
        UUID id,
        UUID recipientUserId,
        NotificationType type,
        NotificationStatus status,
        int attemptCount,
        String lastError,
        UUID sourceEventId,
        String correlationId,
        Instant sentAt
) {

    public Notification {

        if (recipientUserId == null) {
            throw new IllegalArgumentException(
                    "Notification recipient user ID is required."
            );
        }

        if (type == null || status == null) {
            throw new IllegalArgumentException(
                    "Notification type and status are required."
            );
        }

        if (sourceEventId == null) {
            throw new IllegalArgumentException(
                    "Notification source event ID is required."
            );
        }

        if (attemptCount < 0) {
            throw new IllegalArgumentException(
                    "Notification attempt count cannot be negative."
            );
        }

        if (status == NotificationStatus.SENT && sentAt == null) {
            throw new IllegalArgumentException(
                    "A sent notification requires its sending time."
            );
        }
    }

    public static Notification pending(
            UUID recipientUserId,
            NotificationType type,
            UUID sourceEventId,
            String correlationId
    ) {
        return new Notification(
                null,
                recipientUserId,
                type,
                NotificationStatus.PENDING,
                0,
                null,
                sourceEventId,
                correlationId,
                null
        );
    }

    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }

    public Notification sent(
            Instant sentAt
    ) {
        return with(NotificationStatus.SENT, attemptCount + 1, null, sentAt);
    }

    public Notification failed(
            String error
    ) {
        return with(NotificationStatus.FAILED, attemptCount + 1, error, null);
    }

    /**
     * Gives up without attempting delivery, so the attempt count is kept.
     */
    public Notification rejected(
            String reason
    ) {
        return with(NotificationStatus.FAILED, attemptCount, reason, null);
    }

    private Notification with(
            NotificationStatus newStatus,
            int newAttemptCount,
            String newLastError,
            Instant newSentAt
    ) {
        return new Notification(
                id,
                recipientUserId,
                type,
                newStatus,
                newAttemptCount,
                newLastError,
                sourceEventId,
                correlationId,
                newSentAt
        );
    }
}
