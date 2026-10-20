package com.telemed.notifications;

import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationStatus;
import com.telemed.notifications.domain.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.profiles.active=dev"
)
class NotificationPersistenceE2ETest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",
                () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password",
                () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
    }

    @Autowired
    private NotificationRepositoryPort notifications;

    @Test
    void shouldInsertPendingAndUpdateItToSentOnTheSameRow() {
        UUID eventId = UUID.randomUUID();
        String correlationId = "c".repeat(128);

        Notification pending = notifications.save(
                Notification.pending(
                        UUID.randomUUID(),
                        NotificationType.PASSWORD_RECOVERY,
                        eventId,
                        correlationId
                )
        );

        assertThat(pending.id()).isNotNull();
        assertThat(pending.status()).isEqualTo(NotificationStatus.PENDING);

        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        notifications.save(pending.sent(sentAt));

        Notification stored =
                notifications.findBySourceEventId(eventId).orElseThrow();

        assertThat(stored.id()).isEqualTo(pending.id());
        assertThat(stored.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(stored.attemptCount()).isEqualTo(1);
        assertThat(stored.sentAt()).isEqualTo(sentAt);
        assertThat(stored.correlationId()).isEqualTo(correlationId);
    }

    @Test
    void shouldRejectASecondNotificationForTheSameEvent() {
        UUID eventId = UUID.randomUUID();

        notifications.save(pending(eventId));

        assertThatThrownBy(() -> notifications.save(pending(eventId)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldReturnEmptyForUnknownEvent() {
        assertThat(notifications.findBySourceEventId(UUID.randomUUID()))
                .isEmpty();
    }

    private static Notification pending(
            UUID eventId
    ) {
        return Notification.pending(
                UUID.randomUUID(),
                NotificationType.PASSWORD_RECOVERY,
                eventId,
                null
        );
    }
}
