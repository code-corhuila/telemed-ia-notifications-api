package com.telemed.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-17: an appointment event from appointments.events ends in the professional's inbox and in
 * one e-mail to the patient, at the address identity answers, also shown in the patient's inbox.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.profiles.active=dev",
                "notifications.messaging.enabled=true",
                "notifications.identity.client-secret=e2e-identity-client-secret",
                "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"
        }
)
class AppointmentEventsListenerE2ETest {

    private static final String EXCHANGE = "appointments.events";
    private static final String DEAD_LETTER_QUEUE = "notifications.appointment-events.dlq";
    private static final String PATIENT_EMAIL = "patient@example.com";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-alpine");

    static final HttpServer IDENTITY = identity();

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        RABBITMQ.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password", () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
        registry.add("security.jwt.public-key", TestAccessTokens::publicKeyPem);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
        registry.add("notifications.mail.from", () -> "no-reply@telemed.test");
        registry.add("notifications.identity.base-url",
                () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
    }

    @AfterAll
    static void stopIdentity() {
        IDENTITY.stop(0);
    }

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void shouldNotifyBothPeopleOnceEvenIfTheEventArrivesTwice() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID professionalId = UUID.randomUUID();
        String event = created(eventId, appointmentId, patientId, professionalId);

        publish(eventId, "appointment-created", event);
        publish(eventId, "appointment-created", event);

        awaitSentRows(eventId, 2);
        // The second copy is consumed after the first; give it time to (not) send.
        Thread.sleep(1_000);
        assertThat(rows(eventId)).isEqualTo(2);

        Map<String, Object> professional = row(eventId, professionalId);
        assertThat(professional.get("channel")).isEqualTo("IN_APP");
        assertThat(professional.get("type")).isEqualTo("APPOINTMENT_CONFIRMED");
        assertThat(professional.get("subject_type")).isEqualTo("APPOINTMENT");
        assertThat(professional.get("subject_id")).isEqualTo(appointmentId);

        Map<String, Object> patient = row(eventId, patientId);
        assertThat(patient.get("channel")).isEqualTo("EMAIL");
        assertThat(patient.get("type")).isEqualTo("APPOINTMENT_CONFIRMED");
        assertThat(patient.get("subject_type")).isEqualTo("APPOINTMENT");
        assertThat(patient.get("subject_id")).isEqualTo(appointmentId);
        assertThat(patient.get("correlation_id")).isEqualTo("e2e-appointment-trace");

        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo(PATIENT_EMAIL);
        assertThat(received[0].getSubject()).isEqualTo("Tu cita en TeleMed IA está confirmada");
    }

    @Test
    void shouldDeadLetterAMessageThatCanNeverBeNotified() {
        UUID eventId = UUID.randomUUID();

        publish(eventId, "appointment-cancelled", "{\"eventId\": \"" + eventId + "\", \"eventType\": \"AppointmentCancelled\"");

        Message deadLettered = rabbit.receive(DEAD_LETTER_QUEUE, 15_000);

        assertThat(deadLettered).isNotNull();
        assertThat(deadLettered.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(rows(eventId)).isZero();
    }

    private void publish(UUID eventId, String routingKey, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(eventId.toString());
        rabbit.send(EXCHANGE, routingKey, new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private void awaitSentRows(UUID eventId, int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            Integer sent = jdbc.queryForObject(
                    "SELECT count(*) FROM notifications.notification WHERE source_event_id = ? AND status = 'SENT'",
                    Integer.class, eventId);
            if (sent != null && sent == expected) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected " + expected + " sent notification(s) for event " + eventId + ".");
    }

    private int rows(UUID eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM notifications.notification WHERE source_event_id = ?",
                Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private Map<String, Object> row(UUID eventId, UUID recipient) {
        return jdbc.queryForMap("""
                SELECT channel, type, subject_type, subject_id, correlation_id
                FROM notifications.notification WHERE source_event_id = ? AND recipient_user_id = ?
                """, eventId, recipient);
    }

    private static String created(UUID eventId, UUID appointmentId, UUID patientId, UUID professionalId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "AppointmentCreated",
                  "occurredAt": "%s",
                  "correlationId": "e2e-appointment-trace",
                  "version": 1,
                  "payload": {
                    "appointmentId": "%s",
                    "patientId": "%s",
                    "professionalId": "%s",
                    "startDateTime": "2026-11-03T14:00:00Z",
                    "endDateTime": "2026-11-03T14:30:00Z"
                  }
                }
                """.formatted(eventId, Instant.now(), appointmentId, patientId, professionalId);
    }

    /** Plays identity: a service token, and the e-mail of whichever patient is asked for. */
    private static HttpServer identity() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/service-token", exchange -> respond(exchange,
                    "{\"accessToken\": \"e2e-service-token\", \"tokenType\": \"Bearer\", \"expiresAt\": \""
                            + Instant.now().plusSeconds(600) + "\"}"));
            server.createContext("/internal/notification-recipients/", exchange -> {
                String id = exchange.getRequestURI().getPath().substring("/internal/notification-recipients/".length());
                respond(exchange, "{\"id\": \"" + id + "\", \"email\": \"" + PATIENT_EMAIL + "\"}");
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
