package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.domain.model.InAppNotification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * Records IN_APP notifications as delivered. The insert itself is the
 * idempotency check: a concurrent redelivery hits the per-recipient unique
 * constraint of notifications-db and inserts nothing.
 */
// @Component, not @Repository, like NotificationPersistenceAdapter.
@Component
public class InAppNotificationPersistenceAdapter
        implements InAppNotificationPort {

    private static final String INSERT = """
            INSERT INTO notifications.notification
                (recipient_user_id, type, channel, status, sent_at,
                 source_event_id, correlation_id, subject_type, subject_id, subject_at)
            VALUES (?, ?, 'IN_APP', 'SENT', ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT uq_notification_source_event_recipient DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public InAppNotificationPersistenceAdapter(
            JdbcTemplate jdbc
    ) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean record(
            InAppNotification notification
    ) {
        return jdbc.update(INSERT,
                notification.recipientUserId(),
                notification.type().name(),
                Timestamp.from(notification.deliveredAt()),
                notification.sourceEventId(),
                notification.correlationId(),
                notification.subject().type().name(),
                notification.subject().id(),
                notification.subject().at() == null ? null : Timestamp.from(notification.subject().at())
        ) == 1;
    }
}
