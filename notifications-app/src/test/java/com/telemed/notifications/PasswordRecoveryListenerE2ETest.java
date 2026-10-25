package com.telemed.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationStatus;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03: identity's event, through RabbitMQ, ends as one e-mail; a bad message ends in the dead-letter queue. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.profiles.active=dev",
                "notifications.messaging.enabled=true",
                "notifications.identity.client-secret=e2e-identity-client-secret",
                "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
        }
)
class PasswordRecoveryListenerE2ETest {

    private static final String EXCHANGE = "identity.events";
    private static final String ROUTING_KEY = "password-recovery-requested";
    private static final String DEAD_LETTER_QUEUE = "notifications.password-recovery-requested.dlq";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-alpine");

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        RABBITMQ.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",
                () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password",
                () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
        registry.add("security.jwt.public-key", TestAccessTokens::publicKeyPem);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
        registry.add("notifications.mail.from", () -> "no-reply@telemed.test");
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private NotificationRepositoryPort notifications;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldSendOneEmailEvenIfIdentityPublishesTheEventTwice() throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String resetUrl = "https://portal.e2e.test/reset-password#token=" + eventId;

        publish(eventId, envelope(eventId, userId, resetUrl));
        publish(eventId, envelope(eventId, userId, resetUrl));

        Notification sent = awaitNotification(eventId, userId, NotificationStatus.SENT);
        assertThat(sent.attemptCount()).isEqualTo(1);

        // The second copy is consumed after the first; give it time to (not) send.
        Thread.sleep(1_000);
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat((String) received[0].getContent()).contains(resetUrl);
    }

    @Test
    void shouldDeadLetterAMessageThatCanNeverBeDelivered() {

        UUID eventId = UUID.randomUUID();

        publish(eventId, "{\"eventId\": \"" + eventId + "\", \"eventType\": \"PasswordRecoveryRequested\"");

        Message deadLettered = rabbit.receive(DEAD_LETTER_QUEUE, 15_000);

        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE source_event_id = ?", Integer.class, eventId))
                .isZero();
    }

    private void publish(UUID eventId, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(eventId.toString());
        rabbit.send(EXCHANGE, ROUTING_KEY, new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private Notification awaitNotification(UUID eventId, UUID userId, NotificationStatus status)
            throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            Optional<Notification> notification = notifications.findBySourceEventIdAndRecipient(eventId, userId)
                    .filter(found -> found.status() == status);
            if (notification.isPresent()) {
                return notification.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("No " + status + " notification for event " + eventId + ".");
    }

    private static String envelope(UUID eventId, UUID userId, String resetUrl) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PasswordRecoveryRequested",
                  "occurredAt": "%s",
                  "correlationId": "e2e-listener-trace",
                  "version": 1,
                  "payload": {
                    "userId": "%s",
                    "email": "patient@example.com",
                    "recoveryTokenId": "%s",
                    "expiresAt": "%s",
                    "resetUrl": "%s"
                  }
                }
                """.formatted(eventId, Instant.now(), userId, UUID.randomUUID(),
                Instant.now().plus(Duration.ofMinutes(30)), resetUrl);
    }
}
