package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.application.port.in.NotifyDocumentReadyCommand;
import com.telemed.notifications.application.port.in.NotifyDocumentReadyUseCase;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.io.IOException;

/**
 * Consumes ConsultationPdfGenerated from document-generation. A message that
 * can never be notified goes to the dead-letter queue; any other failure is
 * rethrown so the broker retries it.
 */
public class ConsultationPdfGeneratedListener {

    public static final String QUEUE = "notifications.consultation-pdf-generated";

    static final String EVENT_TYPE = "ConsultationPdfGenerated";
    static final int SUPPORTED_VERSION = 1;

    private final NotifyDocumentReadyUseCase notifyDocumentReady;
    private final ObjectMapper objectMapper;

    public ConsultationPdfGeneratedListener(
            NotifyDocumentReadyUseCase notifyDocumentReady,
            ObjectMapper objectMapper
    ) {
        this.notifyDocumentReady = notifyDocumentReady;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = QUEUE)
    public void onMessage(Message message) {

        NotifyDocumentReadyCommand command = toCommand(message);

        MDC.put(CorrelationContext.MDC_KEY, command.correlationId());
        try {
            notifyDocumentReady.notify(command);
        } finally {
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }

    private NotifyDocumentReadyCommand toCommand(Message message) {

        ConsultationPdfGeneratedMessage event;
        try {
            event = objectMapper.readValue(message.getBody(), ConsultationPdfGeneratedMessage.class);
        } catch (IOException exception) {
            throw reject("Malformed ConsultationPdfGenerated message.");
        }

        if (!EVENT_TYPE.equals(event.eventType())
                || event.version() == null
                || event.version() != SUPPORTED_VERSION) {
            throw reject("Unsupported event type or version.");
        }

        if (event.payload() == null) {
            throw reject("ConsultationPdfGenerated without payload.");
        }

        try {
            return new NotifyDocumentReadyCommand(
                    event.eventId(),
                    event.correlationId(),
                    event.payload().documentId(),
                    event.payload().patientId()
            );
        } catch (IllegalArgumentException exception) {
            throw reject("Incomplete ConsultationPdfGenerated event.");
        }
    }

    private static AmqpRejectAndDontRequeueException reject(String reason) {
        return new AmqpRejectAndDontRequeueException(reason);
    }
}
