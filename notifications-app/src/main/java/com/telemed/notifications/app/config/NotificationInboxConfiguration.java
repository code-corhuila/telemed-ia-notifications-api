package com.telemed.notifications.app.config;

import com.telemed.notifications.application.port.out.NotificationInboxPort;
import com.telemed.notifications.application.usecase.NotificationInboxService;
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
}
