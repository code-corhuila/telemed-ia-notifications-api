package com.telemed.notifications.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.messaging.AppointmentEventsListener;
import com.telemed.notifications.adapter.out.email.NotificationMailProperties;
import com.telemed.notifications.adapter.out.email.SmtpAppointmentEmailSender;
import com.telemed.notifications.application.port.in.NotifyAppointmentChangeUseCase;
import com.telemed.notifications.application.port.out.AppointmentEmailSenderPort;
import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.application.port.out.NotificationRepositoryPort;
import com.telemed.notifications.application.port.out.PatientContactPort;
import com.telemed.notifications.application.usecase.NotifyAppointmentChangeService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.util.List;

/**
 * HU-17: consumes the appointment changes from appointment-scheduling, and HU-18 the
 * reminders the worker publishes, on exchange appointments.events (ADR-011).
 * Notifications owns one queue for all of them and the dead-letter queue behind it.
 */
@Configuration
@ConditionalOnProperty(prefix = "notifications.messaging", name = "enabled", havingValue = "true")
public class AppointmentMessagingConfiguration {

    static final String EXCHANGE = "appointments.events";
    static final List<String> ROUTING_KEYS = List.of(
            "appointment-created",
            "appointment-rescheduled",
            "appointment-cancelled",
            "appointment-reminder-due"
    );
    static final String DEAD_LETTER_QUEUE = AppointmentEventsListener.QUEUE + ".dlq";

    // Same declaration as appointment-scheduling's, so either service may start first.
    @Bean
    TopicExchange appointmentsEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue appointmentEventsQueue() {
        return QueueBuilder.durable(AppointmentEventsListener.QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    // No TTL, unlike password recovery: these messages carry ids and times only.
    @Bean
    Queue appointmentEventsDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Declarables appointmentEventsBindings(
            Queue appointmentEventsQueue,
            TopicExchange appointmentsEventsExchange
    ) {
        return new Declarables(ROUTING_KEYS.stream()
                .map(routingKey -> (Binding) BindingBuilder.bind(appointmentEventsQueue)
                        .to(appointmentsEventsExchange)
                        .with(routingKey))
                .toList());
    }

    @Bean
    AppointmentEmailSenderPort appointmentEmailSender(
            JavaMailSender mailSender,
            NotificationMailProperties properties
    ) {
        return new SmtpAppointmentEmailSender(mailSender, properties);
    }

    @Bean
    NotifyAppointmentChangeUseCase notifyAppointmentChangeUseCase(
            InAppNotificationPort inbox,
            NotificationRepositoryPort notifications,
            PatientContactPort patientContact,
            AppointmentEmailSenderPort appointmentEmailSender,
            Clock notificationsClock
    ) {
        return new NotifyAppointmentChangeService(
                inbox, notifications, patientContact, appointmentEmailSender, notificationsClock);
    }

    @Bean
    AppointmentEventsListener appointmentEventsListener(
            NotifyAppointmentChangeUseCase notifyAppointmentChange,
            ObjectMapper objectMapper
    ) {
        return new AppointmentEventsListener(notifyAppointmentChange, objectMapper);
    }
}
