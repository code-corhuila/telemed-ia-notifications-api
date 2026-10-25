package com.telemed.notifications.application.port.out;

import com.telemed.notifications.domain.model.InAppNotification;

public interface InAppNotificationPort {

    /**
     * Idempotent per source event and recipient, also under concurrent redelivery.
     *
     * @return false if that recipient was already notified of that event
     */
    boolean record(
            InAppNotification notification
    );
}
