package com.telemed.notifications.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.messaging.PasswordRecoveryRequestedListener;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailUseCase;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * HU-03: consumes PasswordRecoveryRequested from identity (exchange identity.events,
 * ADR-011). Notifications owns its queue and the dead-letter queue behind it.
 */
@Configuration
@ConditionalOnProperty(prefix = "notifications.messaging", name = "enabled", havingValue = "true")
public class PasswordRecoveryMessagingConfiguration {

    static final String EXCHANGE = "identity.events";
    static final String ROUTING_KEY = "password-recovery-requested";
    static final String DEAD_LETTER_QUEUE = PasswordRecoveryRequestedListener.QUEUE + ".dlq";

    // Same declaration as identity's, so either service may start first.
    @Bean
    TopicExchange identityEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue passwordRecoveryRequestedQueue() {
        return QueueBuilder.durable(PasswordRecoveryRequestedListener.QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    // Dead-lettered messages still carry the reset link: they expire with it.
    @Bean
    Queue passwordRecoveryRequestedDeadLetterQueue(
            @Value("${notifications.messaging.dead-letter-ttl}") Duration deadLetterTtl
    ) {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE)
                .ttl(Math.toIntExact(deadLetterTtl.toMillis()))
                .build();
    }

    @Bean
    Binding passwordRecoveryRequestedBinding(
            Queue passwordRecoveryRequestedQueue,
            TopicExchange identityEventsExchange
    ) {
        return BindingBuilder.bind(passwordRecoveryRequestedQueue)
                .to(identityEventsExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    PasswordRecoveryRequestedListener passwordRecoveryRequestedListener(
            DeliverPasswordRecoveryEmailUseCase deliverPasswordRecoveryEmail,
            ObjectMapper objectMapper
    ) {
        return new PasswordRecoveryRequestedListener(deliverPasswordRecoveryEmail, objectMapper);
    }
}
