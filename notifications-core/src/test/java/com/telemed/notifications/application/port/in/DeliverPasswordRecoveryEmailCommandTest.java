package com.telemed.notifications.application.port.in;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverPasswordRecoveryEmailCommandTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "patient@example.com";
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-20T15:30:00Z");
    private static final String RESET_URL =
            "https://telemed.example.com/reset-password#token=plain-reset-token";

    @Test
    void shouldNotExposeRecipientOrResetLinkInToString() {
        DeliverPasswordRecoveryEmailCommand command =
                new DeliverPasswordRecoveryEmailCommand(
                        EVENT_ID, "trace-123", USER_ID, EMAIL, EXPIRES_AT, RESET_URL
                );

        assertThat(command.toString())
                .contains(EVENT_ID.toString())
                .doesNotContain(EMAIL)
                .doesNotContain("plain-reset-token");
    }

    @Test
    void shouldRequireEventIdRecipientAndLink() {
        assertThatThrownBy(() -> new DeliverPasswordRecoveryEmailCommand(
                null, null, USER_ID, EMAIL, EXPIRES_AT, RESET_URL
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DeliverPasswordRecoveryEmailCommand(
                EVENT_ID, null, USER_ID, " ", EXPIRES_AT, RESET_URL
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DeliverPasswordRecoveryEmailCommand(
                EVENT_ID, null, USER_ID, EMAIL, EXPIRES_AT, null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
