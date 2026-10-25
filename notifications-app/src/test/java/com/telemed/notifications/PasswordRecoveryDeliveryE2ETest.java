package com.telemed.notifications;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailCommand;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailUseCase;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.domain.model.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring of HU-03 delivery: use case, official schema and a real SMTP
 * conversation with GreenMail.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.profiles.active=dev"
)
class PasswordRecoveryDeliveryE2ETest {

    private static final String EMAIL = "patient@example.com";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",
                () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password",
                () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
        registry.add("security.jwt.public-key", TestAccessTokens::publicKeyPem);
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", ServerSetupTest.SMTP::getPort);
        registry.add("notifications.mail.from", () -> "no-reply@telemed.test");
    }

    @Autowired
    private DeliverPasswordRecoveryEmailUseCase deliverPasswordRecoveryEmail;

    @Autowired
    private NotificationRepositoryPort notifications;

    @Test
    void shouldSendTheEmailOnceAndRecordItAsSentEvenIfTheEventIsRedelivered() {
        DeliverPasswordRecoveryEmailCommand command =
                new DeliverPasswordRecoveryEmailCommand(
                        UUID.randomUUID(),
                        "e2e-trace-recovery",
                        UUID.randomUUID(),
                        EMAIL,
                        Instant.now().plus(Duration.ofMinutes(30)),
                        "https://telemed.example.com/reset-password#token=e2e"
                );

        deliverPasswordRecoveryEmail.deliver(command);
        deliverPasswordRecoveryEmail.deliver(command);

        assertThat(GREEN_MAIL.getReceivedMessages()).hasSize(1);

        assertThat(notifications.findBySourceEventId(command.eventId()))
                .get()
                .satisfies(notification -> {
                    assertThat(notification.status())
                            .isEqualTo(NotificationStatus.SENT);
                    assertThat(notification.attemptCount()).isEqualTo(1);
                });
    }
}
