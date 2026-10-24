package com.telemed.notifications.adapter.out.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * @param from     sender address of every notification e-mail
 * @param timeZone zone used to show expiration times to patients
 */
@ConfigurationProperties(prefix = "notifications.mail")
public record NotificationMailProperties(
        String from,
        @DefaultValue("America/Bogota") ZoneId timeZone
) {

    public NotificationMailProperties {

        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException(
                    "notifications.mail.from is required."
            );
        }
    }
}
