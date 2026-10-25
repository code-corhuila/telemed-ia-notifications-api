package com.telemed.notifications.adapter.out.persistence;

import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationType;
import com.telemed.notifications.domain.model.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPersistenceAdapterTest {

    private static final UUID USER_ID =
            UUID.fromString(
                    "11111111-1111-4111-8111-111111111111"
            );

    private static final UUID EVENT_ID =
            UUID.fromString(
                    "33333333-3333-4333-8333-333333333333"
            );

    private static final Instant SENT_AT =
            Instant.parse("2026-10-20T15:00:00Z");

    @Mock
    private SpringDataNotificationRepository repository;

    private NotificationPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationPersistenceAdapter(repository);
    }

    @Test
    void shouldMapSentNotificationToEntityInUtc() {
        when(repository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Notification sent =
                Notification.pending(
                        USER_ID,
                        NotificationType.PASSWORD_RECOVERY,
                        EVENT_ID,
                        "trace-123"
                ).sent(SENT_AT);

        Notification result = adapter.save(sent);

        ArgumentCaptor<NotificationJpaEntity> captor =
                ArgumentCaptor.forClass(NotificationJpaEntity.class);

        verify(repository).saveAndFlush(captor.capture());

        NotificationJpaEntity entity = captor.getValue();
        assertThat(entity.getRecipientUserId()).isEqualTo(USER_ID);
        assertThat(entity.getType()).isEqualTo(NotificationType.PASSWORD_RECOVERY);
        assertThat(entity.getSourceEventId()).isEqualTo(EVENT_ID);
        assertThat(entity.getCorrelationId()).isEqualTo("trace-123");
        assertThat(entity.getAttemptCount()).isEqualTo(1);
        assertThat(entity.getSentAt())
                .isEqualTo(OffsetDateTime.ofInstant(SENT_AT, ZoneOffset.UTC));

        assertThat(result).isEqualTo(sent);
    }

    @Test
    void shouldWriteTheSubjectOfAnAppointmentNotification() {
        when(repository.saveAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        UUID appointmentId = UUID.fromString("55555555-5555-4555-8555-555555555555");

        adapter.save(Notification.pending(
                USER_ID,
                NotificationType.APPOINTMENT_CONFIRMED,
                EVENT_ID,
                null,
                new InboxNotification.Subject(SubjectType.APPOINTMENT, appointmentId, SENT_AT)
        ));

        ArgumentCaptor<NotificationJpaEntity> captor =
                ArgumentCaptor.forClass(NotificationJpaEntity.class);
        verify(repository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().toDomain().subject())
                .isEqualTo(new InboxNotification.Subject(SubjectType.APPOINTMENT, appointmentId, SENT_AT));
    }

    @Test
    void shouldWrapDataAccessFailures() {
        when(repository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        Notification pending =
                Notification.pending(
                        USER_ID,
                        NotificationType.PASSWORD_RECOVERY,
                        EVENT_ID,
                        null
                );

        assertThatThrownBy(() -> adapter.save(pending))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
}
