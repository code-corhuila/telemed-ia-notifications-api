package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-011 envelope of PasswordRecoveryRequested. Unknown fields are ignored, so
 * identity can add one without breaking this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PasswordRecoveryRequestedMessage(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        Integer version,
        Payload payload
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(
            UUID userId,
            String email,
            UUID recoveryTokenId,
            Instant expiresAt,
            String resetUrl
    ) {
    }

    // Records print every component; this one carries the reset link.
    @Override
    public String toString() {
        return "PasswordRecoveryRequestedMessage[eventId=" + eventId
                + ", eventType=" + eventType
                + ", version=" + version + "]";
    }
}
