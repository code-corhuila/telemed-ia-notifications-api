package com.telemed.notifications.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @Test
    void shouldRejectSentNotificationWithoutSentAt() {
        assertThatThrownBy(() -> new Notification(
                null, USER_ID, NotificationType.PASSWORD_RECOVERY,
                NotificationStatus.SENT, 1, null, EVENT_ID, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeAttemptCount() {
        assertThatThrownBy(() -> new Notification(
                null, USER_ID, NotificationType.PASSWORD_RECOVERY,
                NotificationStatus.PENDING, -1, null, EVENT_ID, null, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRequireSourceEventId() {
        assertThatThrownBy(() -> Notification.pending(
                USER_ID, NotificationType.PASSWORD_RECOVERY, null, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepWhatItIsAboutThroughTheDelivery() {
        InboxNotification.Subject appointment = new InboxNotification.Subject(
                SubjectType.APPOINTMENT, UUID.randomUUID(), Instant.parse("2026-11-03T14:00:00Z"));

        Notification pending = Notification.pending(
                USER_ID, NotificationType.APPOINTMENT_CONFIRMED, EVENT_ID, "trace-1", appointment);

        assertThat(pending.subject()).isEqualTo(appointment);
        assertThat(pending.sent(Instant.now()).subject()).isEqualTo(appointment);
        assertThat(pending.failed("EMAIL_DELIVERY_FAILED").subject()).isEqualTo(appointment);
        assertThat(pending.rejected("NO_RECIPIENT_EMAIL").subject()).isEqualTo(appointment);
    }

    @Test
    void shouldNeedNoSubjectForPasswordRecovery() {
        assertThat(Notification.pending(USER_ID, NotificationType.PASSWORD_RECOVERY, EVENT_ID, null).subject())
                .isNull();
    }

    @Test
    void shouldRequireTheSubjectIdentifier() {
        assertThatThrownBy(() -> Notification.pending(
                USER_ID, NotificationType.APPOINTMENT_CONFIRMED, EVENT_ID, null,
                new InboxNotification.Subject(SubjectType.APPOINTMENT, null, null)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
