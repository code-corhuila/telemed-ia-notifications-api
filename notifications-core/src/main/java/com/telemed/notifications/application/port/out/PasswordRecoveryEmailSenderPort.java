package com.telemed.notifications.application.port.out;

import com.telemed.notifications.application.exception.EmailDeliveryException;

import java.time.Instant;

public interface PasswordRecoveryEmailSenderPort {

    /**
     * @throws EmailDeliveryException if the mail server does not accept the message
     */
    void send(
            String recipientEmail,
            String resetUrl,
            Instant expiresAt
    );
}
