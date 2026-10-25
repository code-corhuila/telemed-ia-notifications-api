package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeCommand;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeUseCase;
import com.telemed.notifications.application.port.out.AppointmentEmailSenderPort;
import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.application.port.out.PatientContactPort;
import com.telemed.notifications.domain.model.InAppNotification;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationStatus;
import com.telemed.notifications.domain.model.SubjectType;

import java.time.Clock;
import java.util.Optional;

/**
 * HU-17: the professional sees the change only in the inbox. The patient is e-mailed, and the
 * same delivery record, which carries the appointment, shows it in the patient's inbox.
 */
public class NotifyAppointmentChangeService implements NotifyAppointmentChangeUseCase {

    static final String NO_RECIPIENT_EMAIL = "NO_RECIPIENT_EMAIL";

    // Fixed code: mail server messages may echo the recipient address.
    static final String DELIVERY_FAILED = "EMAIL_DELIVERY_FAILED";

    private final InAppNotificationPort inbox;
    private final NotificationRepositoryPort notifications;
    private final PatientContactPort contacts;
    private final AppointmentEmailSenderPort sender;
    private final Clock clock;

    public NotifyAppointmentChangeService(
            InAppNotificationPort inbox,
            NotificationRepositoryPort notifications,
            PatientContactPort contacts,
            AppointmentEmailSenderPort sender,
            Clock clock
    ) {
        this.inbox = inbox;
        this.notifications = notifications;
        this.contacts = contacts;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public void notify(
            NotifyAppointmentChangeCommand command
    ) {
        InboxNotification.Subject appointment = new InboxNotification.Subject(
                SubjectType.APPOINTMENT, command.appointmentId(), command.startsAt());

        // Idempotent per event and recipient, so a redelivery may record it again.
        inbox.record(new InAppNotification(
                command.professionalId(),
                command.change(),
                appointment,
                command.eventId(),
                command.correlationId(),
                clock.instant()
        ));

        emailPatient(command, appointment);
    }

    private void emailPatient(
            NotifyAppointmentChangeCommand command,
            InboxNotification.Subject appointment
    ) {
        // Recording PENDING first lets the unique (source event, recipient) reject
        // a concurrent redelivery of the same event before a second e-mail.
        Notification notification =
                notifications.findBySourceEventIdAndRecipient(command.eventId(), command.patientId())
                        .orElseGet(() -> notifications.save(Notification.pending(
                                command.patientId(),
                                command.change(),
                                command.eventId(),
                                command.correlationId(),
                                appointment
                        )));

        if (notification.isSent() || hasNoEmailToSendTo(notification)) {
            return;
        }

        // Identity unavailable: rethrown with the record still PENDING, so the broker redelivers.
        Optional<String> email = contacts.findEmail(command.patientId());
        if (email.isEmpty()) {
            notifications.save(notification.rejected(NO_RECIPIENT_EMAIL));
            return;
        }

        try {
            sender.send(email.get(), command.change(), command.startsAt(), command.previousStartsAt());

        } catch (EmailDeliveryException exception) {
            notifications.save(notification.failed(DELIVERY_FAILED));
            // Rethrown so the broker redelivers: the address is never stored.
            throw exception;
        }

        notifications.save(notification.sent(clock.instant()));
    }

    private static boolean hasNoEmailToSendTo(Notification notification) {
        return notification.status() == NotificationStatus.FAILED
                && NO_RECIPIENT_EMAIL.equals(notification.lastError());
    }
}
