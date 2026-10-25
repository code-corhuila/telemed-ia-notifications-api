package com.telemed.notifications.application.port.in;

import com.telemed.notifications.domain.model.NotificationType;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Data of a consumed AppointmentCreated, AppointmentRescheduled or AppointmentCancelled event.
 *
 * @param change           APPOINTMENT_CONFIRMED, APPOINTMENT_RESCHEDULED or APPOINTMENT_CANCELLED
 * @param patientId        identity account of the patient (the token's sub)
 * @param professionalId   identity account of the professional (the token's sub)
 * @param startsAt         start of the appointment; the new start when it was rescheduled
 * @param previousStartsAt the start before it was rescheduled; null otherwise
 */
public record NotifyAppointmentChangeCommand(
        UUID eventId,
        String correlationId,
        NotificationType change,
        UUID appointmentId,
        UUID patientId,
        UUID professionalId,
        Instant startsAt,
        Instant previousStartsAt
) {

    private static final Set<NotificationType> CHANGES = EnumSet.of(
            NotificationType.APPOINTMENT_CONFIRMED,
            NotificationType.APPOINTMENT_RESCHEDULED,
            NotificationType.APPOINTMENT_CANCELLED
    );

    public NotifyAppointmentChangeCommand {

        if (eventId == null || appointmentId == null || patientId == null
                || professionalId == null || startsAt == null) {
            throw new IllegalArgumentException(
                    "Event, appointment, patient, professional and start are required."
            );
        }

        if (!CHANGES.contains(change)) {
            throw new IllegalArgumentException(
                    "Only a confirmed, rescheduled or cancelled appointment is a change."
            );
        }

        if (change == NotificationType.APPOINTMENT_RESCHEDULED && previousStartsAt == null) {
            throw new IllegalArgumentException(
                    "A rescheduled appointment needs its previous start."
            );
        }
    }
}
