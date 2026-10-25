package com.telemed.notifications.application.port.in;

import java.util.UUID;

/**
 * Data of a consumed {@code ConsultationPdfGenerated} event.
 *
 * @param patientId identity account of the patient (the token's sub)
 */
public record NotifyDocumentReadyCommand(
        UUID eventId,
        String correlationId,
        UUID documentId,
        UUID patientId
) {

    public NotifyDocumentReadyCommand {

        if (eventId == null || documentId == null || patientId == null) {
            throw new IllegalArgumentException(
                    "Event ID, document ID and patient ID are required."
            );
        }
    }
}
