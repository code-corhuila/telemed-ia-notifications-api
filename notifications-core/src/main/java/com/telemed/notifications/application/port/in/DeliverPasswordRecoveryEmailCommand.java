package com.telemed.notifications.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Data of a consumed {@code PasswordRecoveryRequested} event.
 *
 * <p>{@code resetUrl} carries the raw one-time token, so neither it nor the
 * recipient address may reach logs: {@link #toString()} leaves them out.
 */
public record DeliverPasswordRecoveryEmailCommand(
        UUID eventId,
        String correlationId,
        UUID userId,
        String email,
        Instant expiresAt,
        String resetUrl
) {

    public DeliverPasswordRecoveryEmailCommand {

        if (eventId == null || userId == null || expiresAt == null) {
            throw new IllegalArgumentException(
                    "Event ID, user ID and expiration are required."
            );
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Recipient e-mail is required."
            );
        }

        if (resetUrl == null || resetUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Password reset URL is required."
            );
        }
    }

    @Override
    public String toString() {
        return "DeliverPasswordRecoveryEmailCommand[eventId=" + eventId
                + ", correlationId=" + correlationId
                + ", userId=" + userId
                + ", expiresAt=" + expiresAt + "]";
    }
}
