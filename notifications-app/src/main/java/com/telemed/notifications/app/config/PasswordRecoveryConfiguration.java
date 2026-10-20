package com.telemed.notifications.app.config;

import com.telemed.notifications.adapter.out.email.NotificationMailProperties;
import com.telemed.notifications.adapter.out.email.SmtpPasswordRecoveryEmailSender;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailUseCase;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.application.port.out.PasswordRecoveryEmailSenderPort;
import com.telemed.notifications.application.usecase.DeliverPasswordRecoveryEmailService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(NotificationMailProperties.class)
public class PasswordRecoveryConfiguration {

    @Bean
    Clock notificationsClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordRecoveryEmailSenderPort passwordRecoveryEmailSender(
            JavaMailSender mailSender,
            NotificationMailProperties properties
    ) {
        return new SmtpPasswordRecoveryEmailSender(
                mailSender,
                properties
        );
    }

    @Bean
    DeliverPasswordRecoveryEmailUseCase deliverPasswordRecoveryEmailUseCase(
            NotificationRepositoryPort notifications,
            PasswordRecoveryEmailSenderPort sender,
            Clock notificationsClock
    ) {
        return new DeliverPasswordRecoveryEmailService(
                notifications,
                sender,
                notificationsClock
        );
    }
}
