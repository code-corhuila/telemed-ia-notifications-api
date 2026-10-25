package com.telemed.notifications;

import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.domain.model.InAppNotification;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** IN_APP notifications on the official notifications-db schema, as notifications_app. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.profiles.active=dev"
)
class InAppNotificationPersistenceE2ETest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    private static final Instant DELIVERED_AT = Instant.parse("2026-10-25T15:00:00Z");

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
    private InAppNotificationPort notifications;

    @Autowired
    private NotificationInboxPort inbox;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldRecordADeliveredInAppNotificationOncePerEventAndRecipient() {
        UUID patient = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        assertThat(notifications.record(documentReady(patient, eventId, documentId))).isTrue();
        assertThat(notifications.record(documentReady(patient, eventId, documentId))).isFalse();
        assertThat(notifications.record(documentReady(UUID.randomUUID(), eventId, documentId))).isTrue();

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT channel, status, sent_at, attempt_count, correlation_id, subject_type, subject_id
                FROM notifications.notification WHERE source_event_id = ? AND recipient_user_id = ?
                """, eventId, patient);
        assertThat(row.get("channel")).isEqualTo("IN_APP");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(((Timestamp) row.get("sent_at")).toInstant()).isEqualTo(DELIVERED_AT);
        assertThat(row.get("attempt_count")).isEqualTo(0);
        assertThat(row.get("correlation_id")).isEqualTo("trace-pdf");
        assertThat(row.get("subject_type")).isEqualTo("DOCUMENT");
        assertThat(row.get("subject_id")).isEqualTo(documentId);
    }

    @Test
    void shouldShowTheNotificationInTheRecipientsInbox() {
        UUID patient = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        notifications.record(documentReady(patient, UUID.randomUUID(), documentId));

        InboxNotification shown = inbox.findPage(patient, NotificationType.inboxTypes(), true, 1, 20)
                .items().get(0);

        assertThat(shown.type()).isEqualTo(NotificationType.DOCUMENT_READY);
        assertThat(shown.subject()).isEqualTo(new InboxNotification.Subject(SubjectType.DOCUMENT, documentId, null));
        assertThat(shown.readAt()).isNull();
    }

    private static InAppNotification documentReady(UUID recipient, UUID eventId, UUID documentId) {
        return new InAppNotification(
                recipient,
                NotificationType.DOCUMENT_READY,
                new InboxNotification.Subject(SubjectType.DOCUMENT, documentId, null),
                eventId,
                "trace-pdf",
                DELIVERED_AT
        );
    }
}
