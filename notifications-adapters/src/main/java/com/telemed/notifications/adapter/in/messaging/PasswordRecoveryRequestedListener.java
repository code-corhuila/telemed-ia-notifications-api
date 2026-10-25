package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailCommand;
import com.telemed.notifications.application.port.in.DeliverPasswordRecoveryEmailUseCase;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;

/**
 * Consumes PasswordRecoveryRequested from identity. A message that can never be
 * delivered goes to the dead-letter queue; an SMTP failure is rethrown so the
 * broker retries it.
 */
public class PasswordRecoveryRequestedListener {

    public static final String QUEUE = "notifications.password-recovery-requested";

    static final String EVENT_TYPE = "PasswordRecoveryRequested";
    static final int SUPPORTED_VERSION = 1;

    private final DeliverPasswordRecoveryEmailUseCase deliverPasswordRecoveryEmail;
    private final ObjectMapper objectMapper;

    public PasswordRecoveryRequestedListener(
            DeliverPasswordRecoveryEmailUseCase deliverPasswordRecoveryEmail,
            ObjectMapper objectMapper
    ) {
        this.deliverPasswordRecoveryEmail = deliverPasswordRecoveryEmail;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {

        DeliverPasswordRecoveryEmailCommand command = toCommand(message);

        MDC.put(CorrelationContext.MDC_KEY, command.correlationId());
        try {
            deliverPasswordRecoveryEmail.deliver(command);
        } finally {
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }

    // Rejections carry no cause: parser and validation messages may quote the reset link.
    private DeliverPasswordRecoveryEmailCommand toCommand(Message message) {

        PasswordRecoveryRequestedMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), PasswordRecoveryRequestedMessage.class);
        } catch (IOException exception) {
            throw reject("Malformed PasswordRecoveryRequested message.");
        }

        if (!EVENT_TYPE.equals(event.eventType())
                || event.version() == null
                || event.version() != SUPPORTED_VERSION) {
            throw reject("Unsupported event type or version.");
        }

        if (event.payload() == null) {
            throw reject("PasswordRecoveryRequested without payload.");
        }

        try {
            return new DeliverPasswordRecoveryEmailCommand(
                    event.eventId(),
                    event.correlationId(),
                    event.payload().userId(),
                    event.payload().email(),
                    event.payload().expiresAt(),
                    event.payload().resetUrl()
            );
        } catch (IllegalArgumentException exception) {
            throw reject("Incomplete PasswordRecoveryRequested event.");
        }
    }

    private static AmqpRejectAndDontRequeueException reject(String reason) {
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
