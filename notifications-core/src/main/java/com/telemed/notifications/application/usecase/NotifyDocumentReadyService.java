package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.port.in.NotifyDocumentReadyCommand;
import com.telemed.notifications.application.port.in.NotifyDocumentReadyUseCase;
import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.domain.model.InAppNotification;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.NotificationType;
import com.telemed.notifications.domain.model.SubjectType;

import java.time.Clock;

/**
 * The consultation PDF is ready: the patient sees it in the inbox only. The
 * portal opens the document from its id; the download link is never stored.
 */
public class NotifyDocumentReadyService implements NotifyDocumentReadyUseCase {

    private final InAppNotificationPort notifications;
    private final Clock clock;

    public NotifyDocumentReadyService(
            InAppNotificationPort notifications,
            Clock clock
    ) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public void notify(
            NotifyDocumentReadyCommand command
    ) {
        notifications.record(new InAppNotification(
                command.patientId(),
                NotificationType.DOCUMENT_READY,
                new InboxNotification.Subject(SubjectType.DOCUMENT, command.documentId(), null),
                command.eventId(),
                command.correlationId(),
                clock.instant()
        ));
    }
}
