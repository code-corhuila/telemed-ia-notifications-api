package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.application.exception.PatientContactUnavailableException;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeCommand;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeUseCase;
import com.telemed.notifications.domain.model.NotificationType;
import org.junit.jupiter.api.Test;
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

/** HU-17: the appointment events, as appointment-scheduling publishes them in the ADR-011 envelope. */
class AppointmentEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID APPOINTMENT_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFESSIONAL_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final NotifyAppointmentChangeUseCase notifyAppointmentChange = mock(NotifyAppointmentChangeUseCase.class);

    private final AppointmentEventsListener listener = new AppointmentEventsListener(
            notifyAppointmentChange,
            new ObjectMapper().registerModule(new JavaTimeModule())
    );

    @Test
    void shouldNotifyACreatedAppointmentAsConfirmed() {

        listener.onMessage(message(envelope("AppointmentCreated", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s",
                "startDateTime": "2026-11-03T14:00:00Z", "endDateTime": "2026-11-03T14:30:00Z"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID))));

        verify(notifyAppointmentChange).notify(command(NotificationType.APPOINTMENT_CONFIRMED,
                "trace-appt", Instant.parse("2026-11-03T14:00:00Z"), null));
    }

    @Test
    void shouldNotifyARescheduledAppointmentWithItsOldAndNewStart() {

        listener.onMessage(message(envelope("AppointmentRescheduled", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s",
                "previousStartDateTime": "2026-11-02T15:30:00.123456Z", "newStartDateTime": "2026-11-03T14:00:00Z"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID))));

        verify(notifyAppointmentChange).notify(command(NotificationType.APPOINTMENT_RESCHEDULED,
                "trace-appt", Instant.parse("2026-11-03T14:00:00Z"), Instant.parse("2026-11-02T15:30:00.123456Z")));
    }

    @Test
    void shouldNotifyACancelledAppointmentWithItsStart() {

        listener.onMessage(message(envelope("AppointmentCancelled", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s",
                "startDateTime": "2026-11-03T14:00:00Z", "cancelledBy": "%s", "cancelledAt": "2026-10-25T15:00:00Z"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID, PATIENT_ID))));

        verify(notifyAppointmentChange).notify(command(NotificationType.APPOINTMENT_CANCELLED,
                "trace-appt", Instant.parse("2026-11-03T14:00:00Z"), null));
    }

    @Test
    void shouldTraceWithTheCorrelationIdOfTheEventAndKeepNoEmptyOne() {

        AtomicReference<String> trace = new AtomicReference<>();
        doAnswer(invocation -> {
            trace.set(MDC.get(CorrelationContext.MDC_KEY));
            return null;
        }).when(notifyAppointmentChange).notify(any());

        listener.onMessage(message(created("trace-appt", PATIENT_ID.toString())));
        assertThat(trace.get()).isEqualTo("trace-appt");
        assertThat(MDC.get(CorrelationContext.MDC_KEY)).isNull();

        listener.onMessage(message(created("", PATIENT_ID.toString())));
        verify(notifyAppointmentChange).notify(command(NotificationType.APPOINTMENT_CONFIRMED,
                null, Instant.parse("2026-11-03T14:00:00Z"), null));
    }

    @Test
    void shouldDeadLetterWhatCanNeverBeNotified() {

        assertRejected("{\"eventId\":");
        assertRejected(envelope("AppointmentCompleted", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s", "completedAt": "2026-11-03T15:00:00Z"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID)));
        assertRejected(created("trace-appt", PATIENT_ID.toString()).replace("\"version\": 1", "\"version\": 2"));
        assertRejected(created("trace-appt", "not-a-uuid"));
        assertRejected("""
                {"eventId": "%s", "eventType": "AppointmentCreated", "version": 1}
                """.formatted(EVENT_ID));
        assertRejected(envelope("AppointmentCreated", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s", "startDateTime": "tomorrow"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID)));
        assertRejected(envelope("AppointmentCreated", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID)));
        assertRejected(envelope("AppointmentRescheduled", 1, "trace-appt", """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s", "newStartDateTime": "2026-11-03T14:00:00Z"
                """.formatted(APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID)));

        verify(notifyAppointmentChange, never()).notify(any());
    }

    @Test
    void shouldLetTheBrokerRetryWhatMayWorkLater() {

        doThrow(new PatientContactUnavailableException("Identity is down.", null))
                .when(notifyAppointmentChange).notify(any());

        assertThatThrownBy(() -> listener.onMessage(message(created("trace-appt", PATIENT_ID.toString()))))
                .isInstanceOf(PatientContactUnavailableException.class)
                .isNotInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private void assertRejected(String body) {
        assertThatThrownBy(() -> listener.onMessage(message(body)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private static NotifyAppointmentChangeCommand command(
            NotificationType change, String correlationId, Instant startsAt, Instant previousStartsAt) {
        return new NotifyAppointmentChangeCommand(EVENT_ID, correlationId, change, APPOINTMENT_ID,
                PATIENT_ID, PROFESSIONAL_ID, startsAt, previousStartsAt);
    }

    private static Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private static String created(String correlationId, String patientId) {
        return envelope("AppointmentCreated", 1, correlationId, """
                "appointmentId": "%s", "patientId": "%s", "professionalId": "%s",
                "startDateTime": "2026-11-03T14:00:00Z", "endDateTime": "2026-11-03T14:30:00Z"
                """.formatted(APPOINTMENT_ID, patientId, PROFESSIONAL_ID));
    }

    private static String envelope(String eventType, int version, String correlationId, String payload) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "2026-10-25T15:00:00.123456Z",
                  "correlationId": "%s",
                  "version": %d,
                  "payload": {%s}
                }
                """.formatted(EVENT_ID, eventType, correlationId, version, payload);
    }
}
