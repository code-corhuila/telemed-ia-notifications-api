package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailCommand;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.application.port.out.PasswordRecoveryEmailSenderPort;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationStatus;
import com.telemed.notifications.domain.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverPasswordRecoveryEmailServiceTest {

    private static final UUID EVENT_ID =
            UUID.fromString(
                    "33333333-3333-4333-8333-333333333333"
            );

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final String EMAIL =
            "patient@example.com";

    private static final String RESET_URL =
            "https://telemed.example.com/reset-password#token=plain-reset-token";

    private static final String CORRELATION_ID =
            "trace-123";

    private static final Instant NOW =
            Instant.parse("2026-10-20T15:00:00Z");

    private static final Instant EXPIRES_AT =
            Instant.parse("2026-10-20T15:30:00Z");

    @Mock
    private NotificationRepositoryPort notifications;

    @Mock
    private PasswordRecoveryEmailSenderPort sender;

    private DeliverPasswordRecoveryEmailService service;

    @BeforeEach
    void setUp() {
        service = new DeliverPasswordRecoveryEmailService(
                notifications,
                sender,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldRecordPendingNotificationBeforeSendingAndMarkItSent() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(notifications.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deliver(command(EXPIRES_AT));

        ArgumentCaptor<Notification> saved =
                ArgumentCaptor.forClass(Notification.class);

        verify(notifications, times(2))
                .save(saved.capture());

        Notification pending = saved.getAllValues().get(0);
        assertThat(pending.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(pending.recipientUserId()).isEqualTo(USER_ID);
        assertThat(pending.type()).isEqualTo(NotificationType.PASSWORD_RECOVERY);
        assertThat(pending.sourceEventId()).isEqualTo(EVENT_ID);
        assertThat(pending.correlationId()).isEqualTo(CORRELATION_ID);

        verify(sender).send(EMAIL, RESET_URL, EXPIRES_AT);

        Notification sent = saved.getAllValues().get(1);
        assertThat(sent.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(sent.attemptCount()).isEqualTo(1);
        assertThat(sent.sentAt()).isEqualTo(NOW);
        assertThat(sent.lastError()).isNull();
    }

    @Test
    void shouldIgnoreEventAlreadySent() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(
                        existing(NotificationStatus.SENT, 1, NOW)
                ));

        service.deliver(command(EXPIRES_AT));

        verifyNoInteractions(sender);
        verify(notifications, never()).save(any());
    }

    @Test
    void shouldRetryPreviouslyFailedNotificationWithoutCreatingAnother() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(
                        existing(NotificationStatus.FAILED, 1, null)
                ));
        when(notifications.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deliver(command(EXPIRES_AT));

        ArgumentCaptor<Notification> saved =
                ArgumentCaptor.forClass(Notification.class);

        verify(notifications).save(saved.capture());
        verify(sender).send(EMAIL, RESET_URL, EXPIRES_AT);

        assertThat(saved.getValue().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(saved.getValue().attemptCount()).isEqualTo(2);
    }

    @Test
    void shouldNotSendExpiredLinkAndRecordItWithoutRetrying() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(notifications.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deliver(command(NOW));

        ArgumentCaptor<Notification> saved =
                ArgumentCaptor.forClass(Notification.class);

        verify(notifications, times(2)).save(saved.capture());
        verifyNoInteractions(sender);

        Notification expired = saved.getAllValues().get(1);
        assertThat(expired.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(expired.lastError())
                .isEqualTo(DeliverPasswordRecoveryEmailService.LINK_EXPIRED);
        assertThat(expired.attemptCount()).isZero();
    }

    @Test
    void shouldRecordFailureWithoutPersonalDataAndRethrowForRedelivery() {
        when(notifications.findBySourceEventIdAndRecipient(EVENT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(notifications.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmailDeliveryException failure =
                new EmailDeliveryException(
                        "Rejected recipient " + EMAIL,
                        new RuntimeException("smtp 550")
                );

        doThrow(failure)
                .when(sender)
                .send(EMAIL, RESET_URL, EXPIRES_AT);

        assertThatThrownBy(() -> service.deliver(command(EXPIRES_AT)))
                .isSameAs(failure);

        ArgumentCaptor<Notification> saved =
                ArgumentCaptor.forClass(Notification.class);

        verify(notifications, times(2)).save(saved.capture());

        Notification failed = saved.getAllValues().get(1);
        assertThat(failed.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(1);
        assertThat(failed.lastError())
                .isEqualTo(DeliverPasswordRecoveryEmailService.DELIVERY_FAILED)
                .doesNotContain(EMAIL);
    }

    private static DeliverPasswordRecoveryEmailCommand command(
            Instant expiresAt
    ) {
        return new DeliverPasswordRecoveryEmailCommand(
                EVENT_ID,
                CORRELATION_ID,
                USER_ID,
                EMAIL,
                expiresAt,
                RESET_URL
        );
    }

    private static Notification existing(
            NotificationStatus status,
            int attemptCount,
            Instant sentAt
    ) {
        return new Notification(
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                USER_ID,
                NotificationType.PASSWORD_RECOVERY,
                status,
                attemptCount,
                status == NotificationStatus.FAILED ? "EMAIL_DELIVERY_FAILED" : null,
                EVENT_ID,
                CORRELATION_ID,
                sentAt,
                null
        );
    }
}
