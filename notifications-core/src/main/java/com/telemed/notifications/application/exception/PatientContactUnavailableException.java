package com.telemed.notifications.application.exception;

/** Identity could not answer now; the event is retried through the broker. */
public class PatientContactUnavailableException extends RuntimeException {

    public PatientContactUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
