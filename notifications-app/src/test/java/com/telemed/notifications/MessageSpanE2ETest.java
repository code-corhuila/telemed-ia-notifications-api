package com.telemed.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 11 traces (CP3): a password recovery message is consumed in a span under identity's
 * publication span, so Jaeger shows request → outbox publication → consumption as one trace.
 */
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
@Import(ExportedSpans.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.profiles.active=dev",
                "notifications.messaging.enabled=true",
                "notifications.identity.client-secret=e2e-identity-client-secret",
                "management.otlp.tracing.export.enabled=false"
        }
)
class MessageSpanE2ETest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String IDENTITY_PUBLISH_SPAN = "00f067aa0ba902b7";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3.12-alpine");

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
    }

    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private InMemorySpanExporter spans;
    @Autowired
    private ObjectProvider<SdkTracerProvider> tracerProvider;

    @Test
    void consumesIdentitysEventInASpanUnderItsPublication(CapturedOutput output) throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(eventId.toString());
        properties.setHeader("traceparent", "00-" + TRACE_ID + "-" + IDENTITY_PUBLISH_SPAN + "-01");
        rabbit.send("identity.events", "password-recovery-requested",
                new Message(envelope(eventId).getBytes(StandardCharsets.UTF_8), properties));

        List<SpanData> consumed = ExportedSpans.await(spans, tracerProvider,
                span -> span.getTraceId().equals(TRACE_ID) && span.getKind() == SpanKind.CONSUMER);

        assertThat(consumed).singleElement().satisfies(span -> {
            assertThat(span.getParentSpanId()).isEqualTo(IDENTITY_PUBLISH_SPAN);
            // The message.consumed line carries that span and the event's correlation id.
            assertThat(output.getOut()).containsPattern("\"span_id\":\"" + span.getSpanId()
                    + "\",\"correlation_id\":\"e2e-span-trace\"[^\\n]*\"event\":\"message.consumed\"");
        });
    }

    private static String envelope(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "PasswordRecoveryRequested",
                  "occurredAt": "%s",
                  "correlationId": "e2e-span-trace",
                  "version": 1,
                  "payload": {
                    "userId": "%s",
                    "email": "patient@example.com",
                    "recoveryTokenId": "%s",
                    "expiresAt": "%s",
                    "resetUrl": "https://portal.e2e.test/reset-password#token=span"
                  }
                }
                """.formatted(eventId, Instant.now(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(Duration.ofMinutes(30)));
    }
}
