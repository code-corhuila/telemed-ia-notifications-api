package com.telemed.notifications.application.exception;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
