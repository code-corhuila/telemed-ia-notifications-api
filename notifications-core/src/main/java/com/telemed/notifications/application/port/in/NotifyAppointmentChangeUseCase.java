package com.telemed.notifications.application.port.in;

public interface NotifyAppointmentChangeUseCase {

    /**
     * A redelivered event sends no second e-mail and adds nothing to the inboxes.
     */
    void notify(
            NotifyAppointmentChangeCommand command
    );
}
