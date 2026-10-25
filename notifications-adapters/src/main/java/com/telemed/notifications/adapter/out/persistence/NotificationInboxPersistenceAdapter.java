package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.domain.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

// @Component, not @Repository, like NotificationPersistenceAdapter.
@Component
public class NotificationInboxPersistenceAdapter
        implements NotificationInboxPort {

    // Ties on created_at are broken by id, so pages never repeat or skip rows.
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final SpringDataNotificationRepository notifications;

    public NotificationInboxPersistenceAdapter(
            SpringDataNotificationRepository notifications
    ) {
        this.notifications = notifications;
    }

    @Override
    @Transactional(readOnly = true)
    public InboxPage findPage(
            UUID recipientUserId,
            Set<NotificationType> types,
            boolean unreadOnly,
            int page,
            int limit
    ) {
        Page<NotificationJpaEntity> rows = notifications.findInbox(
                recipientUserId, types, unreadOnly, PageRequest.of(page - 1, limit, NEWEST_FIRST));

        return new InboxPage(
                rows.map(NotificationJpaEntity::toInboxNotification).getContent(),
                page,
                limit,
                rows.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(
            UUID recipientUserId,
            Set<NotificationType> types
    ) {
        return notifications.countByRecipientUserIdAndTypeInAndReadAtIsNull(recipientUserId, types);
    }

    @Override
    @Transactional
    public boolean markRead(
            UUID recipientUserId,
            UUID notificationId,
            Set<NotificationType> types,
            Instant readAt
    ) {
        return notifications.markRead(notificationId, recipientUserId, types, utc(readAt)) == 1
                || notifications.existsByIdAndRecipientUserIdAndTypeIn(notificationId, recipientUserId, types);
    }

    @Override
    @Transactional
    public int markAllRead(
            UUID recipientUserId,
            Set<NotificationType> types,
            Instant readAt
    ) {
        return notifications.markAllRead(recipientUserId, types, utc(readAt));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
