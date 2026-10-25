package com.telemed.notifications.application.exception;

/**
 * Also raised for another user's notification, so its existence is not disclosed.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Notification not found.");
    }
}
