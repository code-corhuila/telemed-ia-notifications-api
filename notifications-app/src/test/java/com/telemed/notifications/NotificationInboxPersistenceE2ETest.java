package com.telemed.notifications;

import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.NotificationType;
import com.telemed.notifications.domain.model.SubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-17 inbox queries on the official notifications-db schema, as notifications_app. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.profiles.active=dev"
)
class NotificationInboxPersistenceE2ETest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private static final Set<NotificationType> INBOX_TYPES = EnumSet.of(
            NotificationType.APPOINTMENT_CONFIRMED,
            NotificationType.APPOINTMENT_CANCELLED,
            NotificationType.APPOINTMENT_RESCHEDULED,
            NotificationType.APPOINTMENT_REMINDER,
            NotificationType.DOCUMENT_READY
    );

    private static final Instant T0 = Instant.parse("2026-10-25T10:00:00Z");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",
                () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password",
                () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
        registry.add("security.jwt.public-key", TestAccessTokens::publicKeyPem);
    }

    @Autowired
    private NotificationInboxPort inbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldPageOwnInboxNewestFirstWithItsSubject() {
        UUID user = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-11-02T14:30:00Z");
        UUID oldest = insert(user, NotificationType.APPOINTMENT_CONFIRMED, 0, "APPOINTMENT", appointmentId, startsAt);
        UUID middle = insert(user, NotificationType.APPOINTMENT_REMINDER, 1, "APPOINTMENT", appointmentId, startsAt);
        UUID newest = insert(user, NotificationType.DOCUMENT_READY, 2, null, null, null);
        insert(user, NotificationType.PASSWORD_RECOVERY, 3, null, null, null);
        insert(UUID.randomUUID(), NotificationType.APPOINTMENT_CONFIRMED, 4, null, null, null);

        InboxPage first = inbox.findPage(user, INBOX_TYPES, false, 1, 2);
        InboxPage second = inbox.findPage(user, INBOX_TYPES, false, 2, 2);

        assertThat(first.total()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).extracting(InboxNotification::id).containsExactly(newest, middle);
        assertThat(second.items()).extracting(InboxNotification::id).containsExactly(oldest);

        InboxNotification confirmed = second.items().get(0);
        assertThat(confirmed.type()).isEqualTo(NotificationType.APPOINTMENT_CONFIRMED);
        assertThat(confirmed.subject())
                .isEqualTo(new InboxNotification.Subject(SubjectType.APPOINTMENT, appointmentId, startsAt));
        assertThat(confirmed.createdAt()).isEqualTo(T0);
        assertThat(confirmed.readAt()).isNull();
        assertThat(first.items().get(0).subject()).isNull();
    }

    @Test
    void shouldFilterUnreadAndByType() {
        UUID user = UUID.randomUUID();
        UUID read = insert(user, NotificationType.APPOINTMENT_CONFIRMED, 0, null, null, null);
        UUID unread = insert(user, NotificationType.APPOINTMENT_CANCELLED, 1, null, null, null);
        inbox.markRead(user, read, INBOX_TYPES, T0.plusSeconds(60));

        assertThat(inbox.findPage(user, INBOX_TYPES, true, 1, 20).items())
                .extracting(InboxNotification::id).containsExactly(unread);
        assertThat(inbox.findPage(user, Set.of(NotificationType.APPOINTMENT_CONFIRMED), false, 1, 20).items())
                .extracting(InboxNotification::id).containsExactly(read);
        assertThat(inbox.countUnread(user, INBOX_TYPES)).isEqualTo(1);
    }

    @Test
    void shouldMarkOnlyOwnInboxNotificationsAndKeepTheFirstReadTime() {
        UUID user = UUID.randomUUID();
        UUID own = insert(user, NotificationType.APPOINTMENT_REMINDER, 0, null, null, null);
        UUID recovery = insert(user, NotificationType.PASSWORD_RECOVERY, 1, null, null, null);
        UUID foreign = insert(UUID.randomUUID(), NotificationType.APPOINTMENT_REMINDER, 2, null, null, null);
        Instant firstRead = T0.plusSeconds(60);

        assertThat(inbox.markRead(user, own, INBOX_TYPES, firstRead)).isTrue();
        assertThat(inbox.markRead(user, own, INBOX_TYPES, firstRead.plusSeconds(60))).isTrue();
        assertThat(inbox.markRead(user, foreign, INBOX_TYPES, firstRead)).isFalse();
        assertThat(inbox.markRead(user, recovery, INBOX_TYPES, firstRead)).isFalse();
        assertThat(inbox.markRead(user, UUID.randomUUID(), INBOX_TYPES, firstRead)).isFalse();

        assertThat(readAt(own)).isEqualTo(firstRead);
        assertThat(readAt(foreign)).isNull();
        assertThat(readAt(recovery)).isNull();
    }

    @Test
    void shouldMarkAllOwnUnreadInboxNotificationsRead() {
        UUID user = UUID.randomUUID();
        UUID first = insert(user, NotificationType.APPOINTMENT_CONFIRMED, 0, null, null, null);
        UUID second = insert(user, NotificationType.DOCUMENT_READY, 1, null, null, null);
        UUID recovery = insert(user, NotificationType.PASSWORD_RECOVERY, 2, null, null, null);
        UUID foreign = insert(UUID.randomUUID(), NotificationType.DOCUMENT_READY, 3, null, null, null);
        Instant now = T0.plusSeconds(120);

        assertThat(inbox.markAllRead(user, INBOX_TYPES, now)).isEqualTo(2);
        assertThat(inbox.markAllRead(user, INBOX_TYPES, now.plusSeconds(60))).isZero();

        assertThat(readAt(first)).isEqualTo(now);
        assertThat(readAt(second)).isEqualTo(now);
        assertThat(readAt(recovery)).isNull();
        assertThat(readAt(foreign)).isNull();
        assertThat(inbox.countUnread(user, INBOX_TYPES)).isZero();
    }

    private UUID insert(
            UUID recipient,
            NotificationType type,
            int minutesAfterT0,
            String subjectType,
            UUID subjectId,
            Instant subjectAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO notifications.notification
                            (id, recipient_user_id, type, source_event_id, created_at,
                             subject_type, subject_id, subject_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, recipient, type.name(), UUID.randomUUID(),
                Timestamp.from(T0.plusSeconds(60L * minutesAfterT0)),
                subjectType, subjectId, subjectAt == null ? null : Timestamp.from(subjectAt));
        return id;
    }

    private Instant readAt(UUID id) {
        Timestamp readAt = jdbc.queryForObject(
                "SELECT read_at FROM notifications.notification WHERE id = ?", Timestamp.class, id);
        return readAt == null ? null : readAt.toInstant();
    }
}
