package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.port.in.NotifyDocumentReadyCommand;
import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.domain.model.InAppNotification;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.NotificationType;
import com.telemed.notifications.domain.model.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The consultation PDF is ready: the patient sees it in the inbox, with no e-mail. */
@ExtendWith(MockitoExtension.class)
class NotifyDocumentReadyServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final UUID DOCUMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");

    @Mock
    private InAppNotificationPort notifications;

    private NotifyDocumentReadyService service;

    @BeforeEach
    void setUp() {
        service = new NotifyDocumentReadyService(notifications, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldRecordTheNoticeInThePatientsInboxAsDelivered() {
        when(notifications.record(any())).thenReturn(true);

        service.notify(new NotifyDocumentReadyCommand(EVENT_ID, "trace-123", DOCUMENT_ID, PATIENT_ID));

        verify(notifications).record(new InAppNotification(
                PATIENT_ID,
                NotificationType.DOCUMENT_READY,
                new InboxNotification.Subject(SubjectType.DOCUMENT, DOCUMENT_ID, null),
                EVENT_ID,
                "trace-123",
                NOW
        ));
    }

    @Test
    void shouldIgnoreARedeliveredEvent() {
        when(notifications.record(any())).thenReturn(false);

        assertThatCode(() -> service.notify(new NotifyDocumentReadyCommand(EVENT_ID, null, DOCUMENT_ID, PATIENT_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRequireTheEventTheDocumentAndThePatient() {
        assertThatThrownBy(() -> new NotifyDocumentReadyCommand(null, null, DOCUMENT_ID, PATIENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyDocumentReadyCommand(EVENT_ID, null, null, PATIENT_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotifyDocumentReadyCommand(EVENT_ID, null, DOCUMENT_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldOnlyHoldInboxTypesWithTheirSubject() {
        assertThatThrownBy(() -> new InAppNotification(PATIENT_ID, NotificationType.PASSWORD_RECOVERY,
                null, EVENT_ID, null, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InAppNotification(PATIENT_ID, NotificationType.DOCUMENT_READY,
                null, EVENT_ID, null, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
