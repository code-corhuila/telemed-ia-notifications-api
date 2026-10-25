package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.application.port.in.NotifyDocumentReadyCommand;
import com.telemed.notifications.application.port.in.NotifyDocumentReadyUseCase;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
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

/** ConsultationPdfGenerated, as document-generation publishes it in the ADR-011 envelope. */
class ConsultationPdfGeneratedListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final UUID DOCUMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final NotifyDocumentReadyUseCase notifyDocumentReady = mock(NotifyDocumentReadyUseCase.class);

    private final ConsultationPdfGeneratedListener listener = new ConsultationPdfGeneratedListener(
            notifyDocumentReady,
            new ObjectMapper().registerModule(new JavaTimeModule())
    );

    @Test
    void shouldNotifyThePatientOfTheirDocument() {

        listener.onMessage(message(envelope("ConsultationPdfGenerated", 1, PATIENT_ID.toString())));

        verify(notifyDocumentReady).notify(
                new NotifyDocumentReadyCommand(EVENT_ID, "trace-pdf", DOCUMENT_ID, PATIENT_ID));
    }

    @Test
    void shouldTraceWithTheCorrelationIdOfTheEvent() {

        AtomicReference<String> trace = new AtomicReference<>();
        doAnswer(invocation -> {
            trace.set(MDC.get(CorrelationContext.MDC_KEY));
            return null;
        }).when(notifyDocumentReady).notify(any());

        listener.onMessage(message(envelope("ConsultationPdfGenerated", 1, PATIENT_ID.toString())));

        assertThat(trace.get()).isEqualTo("trace-pdf");
        assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();
    }

    @Test
    void shouldDeadLetterWhatCanNeverBeNotified() {

        assertRejected("{\"eventId\":");
        assertRejected(envelope("PasswordRecoveryRequested", 1, PATIENT_ID.toString()));
        assertRejected(envelope("ConsultationPdfGenerated", 2, PATIENT_ID.toString()));
        assertRejected(envelope("ConsultationPdfGenerated", 1, "not-a-uuid"));
        assertRejected("""
                {"eventId": "%s", "eventType": "ConsultationPdfGenerated", "version": 1}
                """.formatted(EVENT_ID));
        assertRejected("""
                {"eventId": "%s", "eventType": "ConsultationPdfGenerated", "version": 1,
                 "payload": {"documentId": "%s"}}
                """.formatted(EVENT_ID, DOCUMENT_ID));

        verify(notifyDocumentReady, never()).notify(any());
    }

    @Test
    void shouldLetTheBrokerRetryWhenTheNotificationCannotBeRecorded() {

        doThrow(new IllegalStateException("database unavailable")).when(notifyDocumentReady).notify(any());

        assertThatThrownBy(() -> listener.onMessage(
                message(envelope("ConsultationPdfGenerated", 1, PATIENT_ID.toString()))))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private void assertRejected(String body) {
        assertThatThrownBy(() -> listener.onMessage(message(body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private static String envelope(String eventType, int version, String patientId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-10-25T15:00:00Z",
                  "correlationId": "trace-pdf",
                  "version": %d,
                  "payload": {
                    "documentId": "%s",
                    "summaryId": "55555555-5555-4555-8555-555555555555",
                    "appointmentId": "66666666-6666-4666-8666-666666666666",
                    "patientId": "%s",
                    "generatedAt": "2026-10-25T14:59:59.123456Z"
                  }
                }
                """.formatted(EVENT_ID, eventType, version, DOCUMENT_ID, patientId);
    }
}
