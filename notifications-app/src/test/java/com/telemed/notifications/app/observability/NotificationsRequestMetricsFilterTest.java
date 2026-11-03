package com.telemed.notifications.app.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/** Week 11 metric catalog (CP4): traffic, errors and latency of the notifications API by route template. */
class NotificationsRequestMetricsFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationsRequestMetricsFilter filter = new NotificationsRequestMetricsFilter(registry);

    @Test
    void countsEachRequestByRouteTemplateMethodAndOutcome() throws Exception {
        serve("GET", "/api/v1/notifications", "/api/v1/notifications", 200);
        serve("GET", "/api/v1/notifications", "/api/v1/notifications", 401);
        serve("GET", "/api/v1/notifications/unread-count", "/api/v1/notifications/unread-count", 503);

        assertThat(count("/api/v1/notifications", "GET", "success")).isEqualTo(1.0);
        assertThat(count("/api/v1/notifications", "GET", "client_error")).isEqualTo(1.0);
        assertThat(count("/api/v1/notifications/unread-count", "GET", "error")).isEqualTo(1.0);
        assertThat(registry.get("notifications.request.duration").tags("route", "/api/v1/notifications")
                .timer().count()).isEqualTo(2);
    }

    @Test
    void groupsUnknownPathsUnderOneSeries() throws Exception {
        serve("GET", "/api/v1/notifications/11111111-1111-4111-8111-111111111111/unknown", null, 404);

        assertThat(count("unmatched", "GET", "client_error")).isEqualTo(1.0);
        assertThat(registry.getMeters()).noneMatch(meter ->
                String.valueOf(meter.getId().getTag("route")).contains("11111111"));
    }

    @Test
    void leavesActuatorRequestsOut() throws Exception {
        serve("GET", "/actuator/health", "/actuator/health", 200);

        assertThat(registry.find("notifications.requests").counters()).isEmpty();
    }

    private void serve(String method, String path, String template, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            if (template != null) {
                req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, template);
            }
            ((MockHttpServletResponse) res).setStatus(status);
        };
        filter.doFilter(request, response, chain);
    }

    private double count(String route, String method, String outcome) {
        return registry.get("notifications.requests")
                .tags("route", route, "method", method, "outcome", outcome)
                .counter()
                .count();
    }
}
