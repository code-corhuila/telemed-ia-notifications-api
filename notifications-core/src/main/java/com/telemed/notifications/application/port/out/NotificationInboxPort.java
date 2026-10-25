package com.telemed.notifications.application.port.out;

import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.domain.model.NotificationType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Reads and marks a recipient's notifications of the given types only. */
public interface NotificationInboxPort {

    /**
     * Newest first.
     */
    InboxPage findPage(
            UUID recipientUserId,
            Set<NotificationType> types,
            boolean unreadOnly,
            int page,
            int limit
    );

    long countUnread(
            UUID recipientUserId,
            Set<NotificationType> types
    );

    /**
     * Sets readAt only if it was unread.
     *
     * @return whether the notification belongs to this recipient and types
     */
    boolean markRead(
            UUID recipientUserId,
            UUID notificationId,
            Set<NotificationType> types,
            Instant readAt
    );

    /**
     * @return how many were unread
     */
    int markAllRead(
            UUID recipientUserId,
            Set<NotificationType> types,
            Instant readAt
    );
}
