package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailCommand;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** HU-03: PasswordRecoveryRequested, as identity publishes it in the ADR-011 envelope. */
class PasswordRecoveryRequestedListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final String RESET_URL = "https://portal.test/reset-password#token=raw-secret";

    private final DeliverPasswordRecoveryEmailUseCase deliver =
            mock(DeliverPasswordRecoveryEmailUseCase.class);

    private final PasswordRecoveryRequestedListener listener = new PasswordRecoveryRequestedListener(
            deliver,
            new ObjectMapper().registerModule(new JavaTimeModule())
    );

    @Test
    void shouldDeliverTheEmailTheEventAsksFor() {

        listener.onMessage(message(envelope("PasswordRecoveryRequested", 1, RESET_URL)));

        ArgumentCaptor<DeliverPasswordRecoveryEmailCommand> captor =
                ArgumentCaptor.forClass(DeliverPasswordRecoveryEmailCommand.class);
        verify(deliver).deliver(captor.capture());

        DeliverPasswordRecoveryEmailCommand command = captor.getValue();
        assertThat(command.eventId()).isEqualTo(EVENT_ID);
        assertThat(command.correlationId()).isEqualTo("trace-123");
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.email()).isEqualTo("patient@example.com");
        assertThat(command.expiresAt()).isEqualTo(Instant.parse("2026-10-25T15:30:00Z"));
        assertThat(command.resetUrl()).isEqualTo(RESET_URL);
    }

    @Test
    void shouldTraceTheDeliveryWithTheCorrelationIdOfTheEvent() {

        AtomicReference<String> traceDuringDelivery = new AtomicReference<>();
        doAnswer(invocation -> {
            traceDuringDelivery.set(MDC.get(CorrelationContext.MDC_KEY));
            return null;
        }).when(deliver).deliver(any());

        listener.onMessage(message(envelope("PasswordRecoveryRequested", 1, RESET_URL)));

        assertThat(traceDuringDelivery.get()).isEqualTo("trace-123");
        assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
    }

    @Test
    void shouldLetTheBrokerRetryWhenTheEmailCouldNotBeSent() {

        EmailDeliveryException failure = new EmailDeliveryException("smtp down", new RuntimeException());
        doThrow(failure).when(deliver).deliver(any());

        assertThatThrownBy(() -> listener.onMessage(message(envelope("PasswordRecoveryRequested", 1, RESET_URL))))
                .isSameAs(failure);
    }

    @Test
    void shouldDeadLetterAMalformedMessageWithoutRepeatingItsContent() {

        Message malformed = message("{\"eventId\": \"" + EVENT_ID + "\", \"payload\": {\"resetUrl\": \"" + RESET_URL);

        assertThatThrownBy(() -> listener.onMessage(malformed))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasNoCause()
                .message().doesNotContain("raw-secret");

        verify(deliver, never()).deliver(any());
    }

    @Test
    void shouldDeadLetterAnotherEventTypeOrVersion() {

        assertThatThrownBy(() -> listener.onMessage(message(envelope("PatientRegistered", 1, RESET_URL))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThatThrownBy(() -> listener.onMessage(message(envelope("PasswordRecoveryRequested", 2, RESET_URL))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);

        verify(deliver, never()).deliver(any());
    }

    @Test
    void shouldDeadLetterAnEventWithoutItsResetLink() {

        assertThatThrownBy(() -> listener.onMessage(message(envelope("PasswordRecoveryRequested", 1, null))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasNoCause();

        verify(deliver, never()).deliver(any());
    }

    private static String envelope(String eventType, int version, String resetUrl) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-10-25T15:00:00Z",
                  "correlationId": "trace-123",
                  "version": %d,
                  "payload": {
                    "userId": "%s",
                    "email": "patient@example.com",
                    "recoveryTokenId": "22222222-2222-4222-8222-222222222222",
                    "expiresAt": "2026-10-25T15:30:00Z",
                    "resetUrl": %s
                  }
                }
                """.formatted(EVENT_ID, eventType, version, USER_ID,
                resetUrl == null ? "null" : "\"" + resetUrl + "\"");
    }

    private static Message message(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(EVENT_ID.toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
