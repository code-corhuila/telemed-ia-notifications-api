package com.telemed.notifications.application.port.in;

import java.util.UUID;

public interface ViewNotificationInboxUseCase {

    InboxPage list(
            UUID recipientUserId,
            InboxQuery query
    );

    long countUnread(
            UUID recipientUserId
    );
}
