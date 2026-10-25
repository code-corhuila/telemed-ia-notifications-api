package com.telemed.notifications.app.config;

import com.telemed.notifications.application.port.in.NotifyDocumentReadyUseCase;
import com.telemed.notifications.application.port.out.InAppNotificationPort;
import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.application.usecase.NotificationInboxService;
import com.telemed.notifications.application.usecase.NotifyDocumentReadyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NotificationInboxConfiguration {

    // One bean for both inbox use cases: ViewNotificationInboxUseCase and MarkNotificationsReadUseCase.
    @Bean
    NotificationInboxService notificationInboxService(
            NotificationInboxPort inbox,
            Clock notificationsClock
    ) {
        return new NotificationInboxService(inbox, notificationsClock);
    }

    @Bean
    NotifyDocumentReadyUseCase notifyDocumentReadyUseCase(
            InAppNotificationPort notifications,
            Clock notificationsClock
    ) {
        return new NotifyDocumentReadyService(notifications, notificationsClock);
    }
}
