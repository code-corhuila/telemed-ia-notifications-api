package com.telemed.notifications.application.port.out;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.domain.model.NotificationType;

import java.time.Instant;

public interface AppointmentEmailSenderPort {

    /**
     * @param change           APPOINTMENT_CONFIRMED, APPOINTMENT_RESCHEDULED, APPOINTMENT_CANCELLED
     *                         or APPOINTMENT_REMINDER
     * @param previousStartsAt the start before it was rescheduled; null otherwise
     * @throws EmailDeliveryException if the mail server does not accept the message
     */
    void send(
            String recipientEmail,
            NotificationType change,
            Instant startsAt,
            Instant previousStartsAt
    );
}
