package com.telemed.notifications.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-03: how notifications consumes PasswordRecoveryRequested from RabbitMQ. */
class MessagingDefaultsTest {

    @Test
    void listenerConsumesOnlyWhereItIsTurnedOn() throws IOException {

        // Off unless NOTIFICATIONS_LISTENER_ENABLED=true: a test run must never take real events.
        assertThat(applicationYml().getProperty("notifications.messaging.enabled", Boolean.class)).isFalse();
    }

    @Test
    void failedDeliveriesAreRetriedThenDeadLetteredBeforeTheLinkExpires() throws IOException {

        MockEnvironment environment = applicationYml();

        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.enabled", Boolean.class))
                .isTrue();
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.retry.max-attempts", Integer.class))
                .isEqualTo(3);
        assertThat(environment.getProperty("spring.rabbitmq.listener.simple.default-requeue-rejected", Boolean.class))
                .isFalse();
        // The dead-lettered message still carries the reset link: it lives no longer than the link.
        assertThat(environment.getProperty("notifications.messaging.dead-letter-ttl")).isEqualTo("PT30M");
    }

    private static MockEnvironment applicationYml() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            environment.getPropertySources().addLast(source);
        }
        return environment;
    }
}
