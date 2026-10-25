package com.telemed.notifications.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification shown only in the inbox, never e-mailed (professionals, the
 * consultation PDF ready notice). It is delivered when it is recorded.
 *
 * @param subject     what it is about, so the portal can open it
 * @param deliveredAt when it was recorded in the inbox
 */
public record InAppNotification(
        UUID recipientUserId,
        NotificationType type,
        InboxNotification.Subject subject,
        UUID sourceEventId,
        String correlationId,
        Instant deliveredAt
) {

    public InAppNotification {

        if (recipientUserId == null || sourceEventId == null || deliveredAt == null) {
            throw new IllegalArgumentException(
                    "Recipient, source event and delivery time are required."
            );
        }

        if (type == null || !type.shownInInbox()) {
            throw new IllegalArgumentException(
                    "An in-app notification needs an inbox type."
            );
        }

        if (subject == null || subject.type() == null || subject.id() == null) {
            throw new IllegalArgumentException(
                    "An in-app notification needs its subject."
            );
        }
    }
}
