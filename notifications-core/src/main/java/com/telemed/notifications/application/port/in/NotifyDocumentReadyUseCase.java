package com.telemed.notifications.application.port.in;

public interface NotifyDocumentReadyUseCase {

    /**
     * A redelivered event is ignored.
     */
    void notify(
            NotifyDocumentReadyCommand command
    );
}
