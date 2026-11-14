package com.telemed.notifications;

import org.junit.jupiter.api.Test;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** ConsultationPdfGenerated from documents.events ends in the patient's inbox, once. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.profiles.active=dev",
                "notifications.messaging.enabled=true",
                "notifications.identity.client-secret=e2e-identity-client-secret",
                "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
        }
)
class DocumentReadyListenerE2ETest {

    private static final String EXCHANGE = "documents.events";
    private static final String ROUTING_KEY = "consultation-pdf-generated";
    private static final String DEAD_LETTER_QUEUE = "notifications.consultation-pdf-generated.dlq";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-alpine");

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
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldNotifyThePatientOnceEvenIfTheEventArrivesTwice() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();

        publish(eventId, envelope(eventId, documentId, patientId));
        publish(eventId, envelope(eventId, documentId, patientId));

        awaitRows(eventId, 1);
        // The second copy is consumed after the first; give it time to (not) record.
        Thread.sleep(1_000);
        assertThat(rows(eventId)).isEqualTo(1);

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT recipient_user_id, type, channel, status, subject_type, subject_id, correlation_id
                FROM notifications.notification WHERE source_event_id = ?
                """, eventId);
        assertThat(row.get("recipient_user_id")).isEqualTo(patientId);
        assertThat(row.get("type")).isEqualTo("DOCUMENT_READY");
        assertThat(row.get("channel")).isEqualTo("IN_APP");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("subject_type")).isEqualTo("DOCUMENT");
        assertThat(row.get("subject_id")).isEqualTo(documentId);
        assertThat(row.get("correlation_id")).isEqualTo("e2e-pdf-trace");
    }

    @Test
    void shouldDeadLetterAMessageThatCanNeverBeNotified() {
        UUID eventId = UUID.randomUUID();

        publish(eventId, "{\"eventId\": \"" + eventId + "\", \"eventType\": \"ConsultationPdfGenerated\"");

        Message deadLettered = rabbit.receive(DEAD_LETTER_QUEUE, 15_000);

        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(rows(eventId)).isZero();
    }

    private void publish(UUID eventId, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(eventId.toString());
        properties.setType("ConsultationPdfGenerated");
        rabbit.send(EXCHANGE, ROUTING_KEY, new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private void awaitRows(UUID eventId, int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            if (rows(eventId) == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected " + expected + " notification(s) for event " + eventId + ".");
    }

    private int rows(UUID eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notifications.notification WHERE source_event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private static String envelope(UUID eventId, UUID documentId, UUID patientId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "ConsultationPdfGenerated",
                  "occurredAt": "2026-10-25T15:00:00Z",
                  "correlationId": "e2e-pdf-trace",
                  "version": 1,
                  "payload": {
                    "documentId": "%s",
                    "summaryId": "%s",
                    "appointmentId": "%s",
                    "patientId": "%s",
                    "generatedAt": "2026-10-25T14:59:59.123456Z"
                  }
                }
                """.formatted(eventId, documentId, UUID.randomUUID(), UUID.randomUUID(), patientId);
    }
}
