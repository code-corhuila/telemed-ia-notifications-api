package com.telemed.notifications.application.exception;

public class InvalidInboxQueryException extends RuntimeException {

    private final String field;

    public InvalidInboxQueryException(
            String field,
            String message
    ) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
