package com.telemed.notifications.app.config;

import com.telemed.notifications.app.observability.NotificationsRequestMetricsFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Week 11 metrics (CP4), scraped from the management port, which deploy publishes on 127.0.0.1 only. */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

    // After the trace context and before authentication, so rejected requests (401, 403) are counted too.
    @Bean
    FilterRegistrationBean<NotificationsRequestMetricsFilter> notificationsRequestMetrics(MeterRegistry registry) {
        FilterRegistrationBean<NotificationsRequestMetricsFilter> registration =
                new FilterRegistrationBean<>(new NotificationsRequestMetricsFilter(registry));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }
}
