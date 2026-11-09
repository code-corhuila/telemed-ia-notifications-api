package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.exception.NotificationNotFoundException;
import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.application.port.in.InboxQuery;
import com.telemed.notifications.application.port.in.MarkNotificationsReadUseCase;
import com.telemed.notifications.application.port.in.ViewNotificationInboxUseCase;
import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.domain.model.NotificationType;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * HU-17 inbox. The recipient always comes from the access token, so a user
 * only ever reaches their own notifications.
 */
public class NotificationInboxService
        implements ViewNotificationInboxUseCase, MarkNotificationsReadUseCase {

    private final NotificationInboxPort inbox;
    private final Clock clock;

    public NotificationInboxService(
            NotificationInboxPort inbox,
            Clock clock
    ) {
        this.inbox = inbox;
        this.clock = clock;
    }

    @Override
    public InboxPage list(
            UUID recipientUserId,
            InboxQuery query
    ) {
        Set<NotificationType> types = query.type() == null
                ? NotificationType.inboxTypes()
                : Set.of(query.type());

        return inbox.findPage(recipientUserId, types, query.unreadOnly(), query.page(), query.limit());
    }

    @Override
    public long countUnread(
            UUID recipientUserId
    ) {
        return inbox.countUnread(recipientUserId, NotificationType.inboxTypes());
    }

    @Override
    public void markRead(
            UUID recipientUserId,
            UUID notificationId
    ) {
        if (!inbox.markRead(recipientUserId, notificationId, NotificationType.inboxTypes(), clock.instant())) {
            throw new NotificationNotFoundException();
        }
    }

    @Override
    public int markAllRead(
            UUID recipientUserId
    ) {
        return inbox.markAllRead(recipientUserId, NotificationType.inboxTypes(), clock.instant());
    }
}
