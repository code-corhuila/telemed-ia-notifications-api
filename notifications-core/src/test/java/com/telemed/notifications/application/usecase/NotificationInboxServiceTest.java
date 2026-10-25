package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.exception.NotificationNotFoundException;
import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.application.port.in.InboxQuery;
import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** HU-17: each user sees and reads only their own inbox; password recovery is never in it. */
@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final UUID NOTIFICATION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");

    private static final Set<NotificationType> INBOX_TYPES = EnumSet.of(
            NotificationType.APPOINTMENT_CONFIRMED,
            NotificationType.APPOINTMENT_CANCELLED,
            NotificationType.APPOINTMENT_RESCHEDULED,
            NotificationType.APPOINTMENT_REMINDER,
            NotificationType.DOCUMENT_READY
    );

    @Mock
    private NotificationInboxPort inbox;

    private NotificationInboxService service;

    @BeforeEach
    void setUp() {
        service = new NotificationInboxService(inbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldListEveryInboxTypeButPasswordRecovery() {

        InboxPage page = new InboxPage(List.of(), 2, 10, 0);
        when(inbox.findPage(USER_ID, INBOX_TYPES, true, 2, 10)).thenReturn(page);

        assertThat(service.list(USER_ID, new InboxQuery(2, 10, true, null))).isSameAs(page);
    }

    @Test
    void shouldListOnlyTheRequestedType() {

        InboxPage page = new InboxPage(List.of(), 1, 20, 0);
        when(inbox.findPage(USER_ID, Set.of(NotificationType.DOCUMENT_READY), false, 1, 20)).thenReturn(page);

        assertThat(service.list(USER_ID, new InboxQuery(1, 20, false, NotificationType.DOCUMENT_READY)))
                .isSameAs(page);
    }

    @Test
    void shouldCountUnreadInboxNotifications() {

        when(inbox.countUnread(USER_ID, INBOX_TYPES)).thenReturn(4L);

        assertThat(service.countUnread(USER_ID)).isEqualTo(4L);
    }

    @Test
    void shouldMarkOneOwnNotificationReadNow() {

        when(inbox.markRead(USER_ID, NOTIFICATION_ID, INBOX_TYPES, NOW)).thenReturn(true);

        service.markRead(USER_ID, NOTIFICATION_ID);

        verify(inbox).markRead(USER_ID, NOTIFICATION_ID, INBOX_TYPES, NOW);
    }

    @Test
    void shouldNotTellAnotherUsersNotificationFromAMissingOne() {

        when(inbox.markRead(USER_ID, NOTIFICATION_ID, INBOX_TYPES, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.markRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void shouldMarkAllOwnUnreadNotificationsRead() {

        when(inbox.markAllRead(USER_ID, INBOX_TYPES, NOW)).thenReturn(3);

        assertThat(service.markAllRead(USER_ID)).isEqualTo(3);
    }
}
