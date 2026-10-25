package com.telemed.notifications.application.usecase;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailCommand;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailUseCase;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.application.port.out.PasswordRecoveryEmailSenderPort;
import com.telemed.notifications.domain.model.Notification;
import com.telemed.notifications.domain.model.NotificationType;

import java.time.Clock;

public class DeliverPasswordRecoveryEmailService
        implements DeliverPasswordRecoveryEmailUseCase {

    static final String LINK_EXPIRED = "LINK_EXPIRED";

    // Fixed code: mail server messages may echo the recipient address.
    static final String DELIVERY_FAILED = "EMAIL_DELIVERY_FAILED";

    private final NotificationRepositoryPort notifications;
    private final PasswordRecoveryEmailSenderPort sender;
    private final Clock clock;

    public DeliverPasswordRecoveryEmailService(
            NotificationRepositoryPort notifications,
            PasswordRecoveryEmailSenderPort sender,
            Clock clock
    ) {
        this.notifications = notifications;
        this.sender = sender;
        this.clock = clock;
    }

    @Override
    public void deliver(
            DeliverPasswordRecoveryEmailCommand command
    ) {

        // Recording PENDING first lets the unique (source event, recipient) reject
        // a concurrent redelivery of the same event before a second e-mail.
        Notification notification =
                notifications.findBySourceEventIdAndRecipient(command.eventId(), command.userId())
                        .orElseGet(() -> notifications.save(
                                Notification.pending(
                                        command.userId(),
                                        NotificationType.PASSWORD_RECOVERY,
                                        command.eventId(),
                                        command.correlationId()
                                )
                        ));

        if (notification.isSent()) {
            return;
        }

        if (!command.expiresAt().isAfter(clock.instant())) {
            notifications.save(notification.rejected(LINK_EXPIRED));
            return;
        }

        try {
            sender.send(
                    command.email(),
                    command.resetUrl(),
                    command.expiresAt()
            );

        } catch (EmailDeliveryException exception) {
            notifications.save(notification.failed(DELIVERY_FAILED));
            // Rethrown so the broker redelivers: the link is never stored.
            throw exception;
        }

        notifications.save(notification.sent(clock.instant()));
    }
}
