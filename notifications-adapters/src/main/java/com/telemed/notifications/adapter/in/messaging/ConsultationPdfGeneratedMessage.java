package com.telemed.notifications.adapter.in.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * ADR-011 envelope of ConsultationPdfGenerated (document-generation). Unknown
 * fields are ignored, so document-generation can add one without breaking
 * this consumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ConsultationPdfGeneratedMessage(
        UUID eventId,
        String eventType,
        String correlationId,
        Integer version,
        Payload payload
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(
            UUID documentId,
            UUID patientId
    ) {
    }
}
