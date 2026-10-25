package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeCommand;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeUseCase;
import com.telemed.notifications.domain.model.NotificationType;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;
import java.time.Instant;

/**
 * Consumes the appointment changes from appointment-scheduling (HU-17). A message
 * that can never be notified goes to the dead-letter queue; any other failure is
 * rethrown so the broker retries it.
 */
public class AppointmentEventsListener {

    public static final String QUEUE = "notifications.appointment-events";

    static final int SUPPORTED_VERSION = 1;

    private final NotifyAppointmentChangeUseCase notifyAppointmentChange;
    private final ObjectMapper objectMapper;

    public AppointmentEventsListener(
            NotifyAppointmentChangeUseCase notifyAppointmentChange,
            ObjectMapper objectMapper
    ) {
        this.notifyAppointmentChange = notifyAppointmentChange;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {

        NotifyAppointmentChangeCommand command = toCommand(message);

        MDC.put(CorrelationContext.MDC_KEY, command.correlationId());
        try {
            notifyAppointmentChange.notify(command);
        } finally {
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }

    private NotifyAppointmentChangeCommand toCommand(Message message) {

        AppointmentEventMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), AppointmentEventMessage.class);
        } catch (IOException exception) {
            throw reject("Malformed appointment event.");
        }

        if (event.version() == null || event.version() != SUPPORTED_VERSION) {
            throw reject("Unsupported event version.");
        }

        AppointmentEventMessage.Payload payload = event.payload();
        if (payload == null) {
            throw reject("Appointment event without payload.");
        }

        NotificationType change;
        Instant startsAt;
        Instant previousStartsAt = null;
        switch (String.valueOf(event.eventType())) {
            case "AppointmentCreated" -> {
                change = NotificationType.APPOINTMENT_CONFIRMED;
                startsAt = payload.startDateTime();
            }
            case "AppointmentRescheduled" -> {
                change = NotificationType.APPOINTMENT_RESCHEDULED;
                startsAt = payload.newStartDateTime();
                previousStartsAt = payload.previousStartDateTime();
            }
            case "AppointmentCancelled" -> {
                change = NotificationType.APPOINTMENT_CANCELLED;
                startsAt = payload.startDateTime();
            }
            default -> throw reject("Unsupported event type.");
        }

        try {
            return new NotifyAppointmentChangeCommand(
                    event.eventId(),
                    // appointment-scheduling may send an empty one; it is no trace.
                    event.correlationId() == null || event.correlationId().isBlank() ? null : event.correlationId(),
                    change,
                    payload.appointmentId(),
                    payload.patientId(),
                    payload.professionalId(),
                    startsAt,
                    previousStartsAt
            );
        } catch (IllegalArgumentException exception) {
            throw reject("Incomplete appointment event.");
        }
    }

    private static AmqpRejectAndDontRequeueException reject(String reason) {
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
