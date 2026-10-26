package com.telemed.notifications.adapter.out.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param baseUrl      identity (identity-api) inside the platform network
 * @param clientId     notifications' service client in identity's SERVICE_CLIENTS
 * @param clientSecret its secret; identity only keeps the bcrypt hash
 * @param timeout      connect and read timeout of every call to identity
 */
@ConfigurationProperties(prefix = "notifications.identity")
public record IdentityClientProperties(
        String baseUrl,
        String clientId,
        String clientSecret,
        Duration timeout
) {

    public IdentityClientProperties {

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("notifications.identity.base-url is required.");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("notifications.identity.client-id is required.");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("notifications.identity.client-secret is required.");
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(3);
        }
    }

    /** The secret never reaches a log line. */
    @Override
    public String toString() {
        return "IdentityClientProperties[baseUrl=" + baseUrl + ", clientId=" + clientId
                + ", clientSecret=[REDACTED], timeout=" + timeout + "]";
    }
}
