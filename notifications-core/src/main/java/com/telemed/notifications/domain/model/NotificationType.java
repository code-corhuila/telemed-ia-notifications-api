package com.telemed.notifications.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum NotificationType {
    PASSWORD_RECOVERY,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_RESCHEDULED,
    APPOINTMENT_REMINDER,
    DOCUMENT_READY;

    /**
     * Password recovery is an e-mail only: the inbox never shows it.
     */
    public boolean shownInInbox() {
        return this != PASSWORD_RECOVERY;
    }

    public static Set<NotificationType> inboxTypes() {
        return EnumSet.complementOf(EnumSet.of(PASSWORD_RECOVERY));
    }
}
