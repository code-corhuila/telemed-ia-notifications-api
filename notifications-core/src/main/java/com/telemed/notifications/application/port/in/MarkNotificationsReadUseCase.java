package com.telemed.notifications.application.port.in;

import com.telemed.notifications.application.exception.NotificationNotFoundException;

import java.util.UUID;

public interface MarkNotificationsReadUseCase {

    /**
     * Idempotent: a notification already read keeps its first read time.
     *
     * @throws NotificationNotFoundException if it is not in this user's inbox
     */
    void markRead(
            UUID recipientUserId,
            UUID notificationId
    );

    /**
     * @return how many notifications were unread
     */
    int markAllRead(
            UUID recipientUserId
    );
}
