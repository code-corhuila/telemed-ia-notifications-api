package com.telemed.notifications.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.messaging.ConsultationPdfGeneratedListener;
import com.telemed.notifications.application.port.in.NotifyDocumentReadyUseCase;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HU-17: consumes ConsultationPdfGenerated from document-generation (exchange
 * documents.events, ADR-011). Notifications owns its queue and the dead-letter
 * queue behind it; document-generation keeps the event in its outbox until
 * this queue is bound.
 */
@Configuration
@ConditionalOnProperty(prefix = "notifications.messaging", name = "enabled", havingValue = "true")
public class DocumentReadyMessagingConfiguration {

    static final String EXCHANGE = "documents.events";
    static final String ROUTING_KEY = "consultation-pdf-generated";
    static final String DEAD_LETTER_QUEUE = ConsultationPdfGeneratedListener.QUEUE + ".dlq";

    // Same declaration as document-generation's, so either service may start first.
    @Bean
    TopicExchange documentsEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue consultationPdfGeneratedQueue() {
        return QueueBuilder.durable(ConsultationPdfGeneratedListener.QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    // No TTL, unlike password recovery: these messages carry ids only.
    @Bean
    Queue consultationPdfGeneratedDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding consultationPdfGeneratedBinding(
            Queue consultationPdfGeneratedQueue,
            TopicExchange documentsEventsExchange
    ) {
        return BindingBuilder.bind(consultationPdfGeneratedQueue)
                .to(documentsEventsExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    ConsultationPdfGeneratedListener consultationPdfGeneratedListener(
            NotifyDocumentReadyUseCase notifyDocumentReady,
            ObjectMapper objectMapper
    ) {
        return new ConsultationPdfGeneratedListener(notifyDocumentReady, objectMapper);
    }
}
