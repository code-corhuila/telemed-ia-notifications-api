package com.telemed.notifications.application.port.in;

public interface DeliverPasswordRecoveryEmailUseCase {

    void deliver(
            DeliverPasswordRecoveryEmailCommand command
    );
}
