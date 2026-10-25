package com.telemed.notifications.application.exception;

/** The token is missing, forged, expired or not a user's access token. */
public class AccessTokenVerificationException extends RuntimeException {

    public AccessTokenVerificationException(String message) {
        super(message);
    }

    public AccessTokenVerificationException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
