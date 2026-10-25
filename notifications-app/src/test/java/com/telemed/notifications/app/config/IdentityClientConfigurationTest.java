package com.telemed.notifications.app.config;

import com.telemed.notifications.application.port.out.PatientContactPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-17: the event consumers ask identity for the patient's e-mail with notifications' service client. */
class IdentityClientConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(IdentityClientConfiguration.class)
            .withPropertyValues(
                    "notifications.identity.base-url=http://identity-and-access-api:8081",
                    "notifications.identity.client-id=notifications");

    @Test
    void shouldAskIdentityWhereTheConsumersRun() {

        context.withPropertyValues(
                        "notifications.messaging.enabled=true",
                        "notifications.identity.client-secret=test-only-secret")
                .run(started -> assertThat(started).hasNotFailed().hasSingleBean(PatientContactPort.class));
    }

    @Test
    void shouldNotStartTheConsumersWithoutTheClientSecret() {

        context.withPropertyValues("notifications.messaging.enabled=true")
                .run(started -> assertThat(started).hasFailed());
    }

    @Test
    void shouldNeedNoIdentityClientWithoutConsumers() {

        context.run(started -> assertThat(started).hasNotFailed().doesNotHaveBean(PatientContactPort.class));
    }

    @Test
    void shouldReachIdentityOnThePlatformNetworkAsNotificationsByDefault() throws IOException {

        PropertySource<?> yml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).get(0);

        assertThat(yml.getProperty("notifications.identity.base-url"))
                .hasToString("${IDENTITY_BASE_URL:http://identity-and-access-api:8081}");
        assertThat(yml.getProperty("notifications.identity.client-id"))
                .hasToString("${IDENTITY_CLIENT_ID:notifications}");
        assertThat(yml.getProperty("notifications.identity.client-secret"))
                .hasToString("${IDENTITY_CLIENT_SECRET:}");
        assertThat(yml.getProperty("notifications.identity.timeout"))
                .hasToString("${IDENTITY_TIMEOUT:3s}");
    }
}
