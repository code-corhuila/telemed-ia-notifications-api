package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.exception.PatientContactUnavailableException;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeCommand;
import com.telemed.notifications.application.port.out.AppointmentEmailSenderPort;
import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.application.port.out.PatientContactPort;
import com.telemed.notifications.domain.model.InAppNotification;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationStatus;
import com.telemed.notifications.domain.model.NotificationType;
import com.telemed.notifications.domain.model.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-17: an appointment was confirmed, rescheduled or cancelled. The professional sees it in the
 * inbox only; the patient gets an e-mail, and the same record shows it in the patient's inbox.
 */
@ExtendWith(MockitoExtension.class)
class NotifyAppointmentChangeServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID APPOINTMENT_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFESSIONAL_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final Instant STARTS_AT = Instant.parse("2026-11-03T14:00:00Z");
    private static final Instant PREVIOUS_START = Instant.parse("2026-11-02T15:30:00Z");
    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");
    private static final String EMAIL = "laura@telemed.co";

    private static final InboxNotification.Subject APPOINTMENT =
            new InboxNotification.Subject(SubjectType.APPOINTMENT, APPOINTMENT_ID, STARTS_AT);

    @Mock
    private InAppNotificationPort inbox;

    @Mock
    private NotificationRepositoryPort notifications;

    @Mock
    private PatientContactPort contacts;

    @Mock
    private AppointmentEmailSenderPort sender;

    private NotifyAppointmentChangeService service;

    @BeforeEach
    void setUp() {
        service = new NotifyAppointmentChangeService(
                inbox, notifications, contacts, sender, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(notifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldShowTheChangeOnlyInTheProfessionalsInbox() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.empty());
        when(contacts.findEmail(PATIENT_ID)).thenReturn(Optional.of(EMAIL));

        service.notify(confirmed());

        verify(inbox).record(new InAppNotification(
                PROFESSIONAL_ID, NotificationType.APPOINTMENT_CONFIRMED, APPOINTMENT, EVENT_ID, "trace-1", NOW));
        verify(contacts, never()).findEmail(PROFESSIONAL_ID);
        verify(sender, times(1)).send(anyString(), any(), any(), any());
    }

    @Test
    void shouldEmailThePatientAndRecordItAsSentWithTheAppointment() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.empty());
        when(contacts.findEmail(PATIENT_ID)).thenReturn(Optional.of(EMAIL));

        service.notify(confirmed());

        verify(sender).send(EMAIL, NotificationType.APPOINTMENT_CONFIRMED, STARTS_AT, null);
        List<Notification> saved = savedRecords(2);
        assertThat(saved.get(0).status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.get(1)).satisfies(sent -> {
            assertThat(sent.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(sent.sentAt()).isEqualTo(NOW);
            assertThat(sent.recipientUserId()).isEqualTo(PATIENT_ID);
            assertThat(sent.type()).isEqualTo(NotificationType.APPOINTMENT_CONFIRMED);
            assertThat(sent.subject()).isEqualTo(APPOINTMENT);
            assertThat(sent.correlationId()).isEqualTo("trace-1");
        });
    }

    @Test
    void shouldTellThePatientTheOldAndTheNewTimeOfARescheduledAppointment() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.empty());
        when(contacts.findEmail(PATIENT_ID)).thenReturn(Optional.of(EMAIL));

        service.notify(command(NotificationType.APPOINTMENT_RESCHEDULED, PREVIOUS_START));

        verify(sender).send(EMAIL, NotificationType.APPOINTMENT_RESCHEDULED, STARTS_AT, PREVIOUS_START);
        verify(inbox).record(new InAppNotification(
                PROFESSIONAL_ID, NotificationType.APPOINTMENT_RESCHEDULED, APPOINTMENT, EVENT_ID, "trace-1", NOW));
    }

    @Test
    void shouldSendNoSecondEmailForARedeliveredEvent() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID))
                .thenReturn(Optional.of(patientRecord().sent(NOW)));

        service.notify(confirmed());

        verify(contacts, never()).findEmail(any());
        verify(sender, never()).send(anyString(), any(), any(), any());
        verify(notifications, never()).save(any());
    }

    @Test
    void shouldKeepTheNoticeInTheInboxWhenThePatientHasNoEmailToSendTo() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.empty());
        when(contacts.findEmail(PATIENT_ID)).thenReturn(Optional.empty());

        service.notify(confirmed());

        verify(sender, never()).send(anyString(), any(), any(), any());
        assertThat(savedRecords(2).get(1)).satisfies(rejected -> {
            assertThat(rejected.status()).isEqualTo(NotificationStatus.FAILED);
            assertThat(rejected.lastError()).isEqualTo(NotifyAppointmentChangeService.NO_RECIPIENT_EMAIL);
            assertThat(rejected.attemptCount()).isZero();
            assertThat(rejected.subject()).isEqualTo(APPOINTMENT);
        });
    }

    @Test
    void shouldNotAskAgainForAPatientWithoutEmail() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID))
                .thenReturn(Optional.of(patientRecord().rejected(NotifyAppointmentChangeService.NO_RECIPIENT_EMAIL)));

        service.notify(confirmed());

        verify(contacts, never()).findEmail(any());
        verify(notifications, never()).save(any());
    }

    @Test
    void shouldLeaveTheEmailPendingWhenIdentityCannotAnswer() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.empty());
        when(contacts.findEmail(PATIENT_ID))
                .thenThrow(new PatientContactUnavailableException("Identity is down.", null));

        // Rethrown so the broker redelivers the event later.
        assertThatThrownBy(() -> service.notify(confirmed()))
                .isInstanceOf(PatientContactUnavailableException.class);

        assertThat(savedRecords(1).get(0).status()).isEqualTo(NotificationStatus.PENDING);
        verify(sender, never()).send(anyString(), any(), any(), any());
    }

    @Test
    void shouldRecordAFailedDeliveryAndRetryItOnRedelivery() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.empty());
        when(contacts.findEmail(PATIENT_ID)).thenReturn(Optional.of(EMAIL));
        doThrow(new EmailDeliveryException("SMTP is down.", null))
                .when(sender).send(EMAIL, NotificationType.APPOINTMENT_CANCELLED, STARTS_AT, null);

        assertThatThrownBy(() -> service.notify(command(NotificationType.APPOINTMENT_CANCELLED, null)))
                .isInstanceOf(EmailDeliveryException.class);

        Notification failed = savedRecords(2).get(1);
        assertThat(failed.status()).isEqualTo(NotificationStatus.FAILED);
        // Fixed code: mail server messages may echo the recipient address.
        assertThat(failed.lastError()).isEqualTo(NotifyAppointmentChangeService.DELIVERY_FAILED);
        assertThat(failed.attemptCount()).isEqualTo(1);
    }

    @Test
    void shouldRetryTheEmailOfARecordWhoseDeliveryFailed() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, PATIENT_ID)).thenReturn(Optional.of(
                patientRecord().failed(NotifyAppointmentChangeService.DELIVERY_FAILED)));
        when(contacts.findEmail(PATIENT_ID)).thenReturn(Optional.of(EMAIL));

        service.notify(confirmed());

        verify(sender).send(EMAIL, NotificationType.APPOINTMENT_CONFIRMED, STARTS_AT, null);
        assertThat(savedRecords(1).get(0)).satisfies(sent -> {
            assertThat(sent.status()).isEqualTo(NotificationStatus.SENT);
            assertThat(sent.attemptCount()).isEqualTo(2);
        });
    }

    @Test
    void shouldAcceptOnlyAppointmentChangesWithTheirData() {
        assertThatThrownBy(() -> command(NotificationType.DOCUMENT_READY, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(NotificationType.APPOINTMENT_REMINDER, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> command(NotificationType.APPOINTMENT_RESCHEDULED, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyAppointmentChangeCommand(EVENT_ID, null,
                NotificationType.APPOINTMENT_CONFIRMED, APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyAppointmentChangeCommand(EVENT_ID, null,
                NotificationType.APPOINTMENT_CONFIRMED, APPOINTMENT_ID, null, PROFESSIONAL_ID, STARTS_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyAppointmentChangeCommand(null, null,
                NotificationType.APPOINTMENT_CONFIRMED, APPOINTMENT_ID, PATIENT_ID, PROFESSIONAL_ID, STARTS_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NotifyAppointmentChangeCommand confirmed() {
        return command(NotificationType.APPOINTMENT_CONFIRMED, null);
    }

    private static NotifyAppointmentChangeCommand command(NotificationType change, Instant previousStartsAt) {
        return new NotifyAppointmentChangeCommand(EVENT_ID, "trace-1", change, APPOINTMENT_ID,
                PATIENT_ID, PROFESSIONAL_ID, STARTS_AT, previousStartsAt);
    }

    private static Notification patientRecord() {
        return Notification.pending(PATIENT_ID, NotificationType.APPOINTMENT_CONFIRMED, EVENT_ID, "trace-1",
                APPOINTMENT);
    }

    private List<Notification> savedRecords(int count) {
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(count)).save(saved.capture());
        return saved.getAllValues();
    }
}
