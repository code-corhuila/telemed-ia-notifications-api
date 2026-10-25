package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-011 envelope of AppointmentCreated, AppointmentRescheduled and AppointmentCancelled
 * (appointment-scheduling). Unknown fields are ignored, so appointment-scheduling can add
 * one without breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AppointmentEventMessage(
        UUID eventId,
        String eventType,
        String correlationId,
        Integer version,
        Payload payload
) {

    /**
     * @param startDateTime         created and cancelled
     * @param previousStartDateTime rescheduled
     * @param newStartDateTime      rescheduled
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(
            UUID appointmentId,
            UUID patientId,
            UUID professionalId,
            Instant startDateTime,
            Instant previousStartDateTime,
            Instant newStartDateTime
    ) {
    }
}
