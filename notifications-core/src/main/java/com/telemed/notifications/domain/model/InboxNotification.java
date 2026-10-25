package com.telemed.notifications.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification as its recipient sees it in the inbox. It holds no names
 * or text: the portal builds them from the type and the subject.
 *
 * @param subject what it is about, or null
 * @param readAt  first time the recipient read it, or null if unread
 */
public record InboxNotification(
        UUID id,
        NotificationType type,
        Subject subject,
        Instant readAt,
        Instant createdAt
) {

    /**
     * @param at the moment shown with the notification (the appointment start), or null
     */
    public record Subject(
            SubjectType type,
            UUID id,
            Instant at
    ) {
    }
}
