package com.telemed.notifications.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Test
    void shouldRejectSentNotificationWithoutSentAt() {
        assertThatThrownBy(() -> new Notification(
                null, USER_ID, NotificationType.PASSWORD_RECOVERY,
                NotificationStatus.SENT, 1, null, EVENT_ID, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeAttemptCount() {
        assertThatThrownBy(() -> new Notification(
                null, USER_ID, NotificationType.PASSWORD_RECOVERY,
                NotificationStatus.PENDING, -1, null, EVENT_ID, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireSourceEventId() {
        assertThatThrownBy(() -> Notification.pending(
                USER_ID, NotificationType.PASSWORD_RECOVERY, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
