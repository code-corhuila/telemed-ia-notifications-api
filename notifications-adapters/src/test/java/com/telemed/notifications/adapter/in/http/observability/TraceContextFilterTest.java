package com.telemed.notifications.adapter.in.http.observability;

import com.telemed.notifications.adapter.CapturedLogs;
import com.telemed.notifications.adapter.in.http.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Week 11 log contract (CP2): every line of a request carries its trace_id, span_id and correlation_id. */
class TraceContextFilterTest {

    private static final String CALLER_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final CorrelationIdFilter correlation = new CorrelationIdFilter();
    private final TraceContextFilter filter = new TraceContextFilter();
    private final Map<String, String> seenByTheRequest = new HashMap<>();
    private final FilterChain chain = (request, response) -> seenByTheRequest.putAll(MDC.getCopyOfContextMap());

    @Test
    void continuesTheCallersTraceWithItsOwnSpan() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        request.addHeader("traceparent", "00-" + CALLER_TRACE + "-00f067aa0ba902b7-01");
        request.addHeader("X-Correlation-Id", "corr-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        serve(request, response, chain);

        assertThat(seenByTheRequest).containsEntry("trace_id", CALLER_TRACE)
                .containsEntry("correlation_id", "corr-0001");
        assertThat(seenByTheRequest.get("span_id")).matches("[0-9a-f]{16}").isNotEqualTo("00f067aa0ba902b7");
        assertThat(response.getHeader("traceparent"))
                .isEqualTo("00-" + CALLER_TRACE + "-" + seenByTheRequest.get("span_id") + "-01");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("corr-0001");
    }

    @Test
    void startsANewTraceWhenTheCallerSentNoneOrAnInvalidOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications/unread-count");
        request.addHeader("traceparent", "00-" + "0".repeat(32) + "-00f067aa0ba902b7-01");

        serve(request, new MockHttpServletResponse(), chain);

        assertThat(seenByTheRequest.get("trace_id")).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        // The correlation id CorrelationIdFilter generated, also used as the error envelope's traceId.
        assertThat(seenByTheRequest.get("correlation_id")).isEqualTo(seenByTheRequest.get("traceId"));
    }

    @Test
    void logsAServerErrorAsAnErrorAndAClientErrorAsInfo() throws Exception {
        try (CapturedLogs logs = CapturedLogs.of(TraceContextFilter.class)) {
            serve(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (request, response) -> ((MockHttpServletResponse) response).setStatus(503));
            serve(new MockHttpServletRequest(), new MockHttpServletResponse(),
                    (request, response) -> ((MockHttpServletResponse) response).setStatus(401));

            assertThat(logs.events("http.request.completed"))
                    .extracting(event -> event.get("level"), event -> event.get("status"),
                            event -> event.get("route"))
                    .containsExactly(tuple("ERROR", 503, "unmatched"), tuple("INFO", 401, "unmatched"));
        }
    }

    @Test
    void leavesNothingBehindForTheNextRequestOnTheSameThread() throws Exception {
        serve(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("correlation_id")).isNull();
    }

    // CorrelationIdFilter runs first in the application, as both filters' @Order say.
    private void serve(MockHttpServletRequest request, MockHttpServletResponse response, FilterChain inner)
            throws Exception {
        correlation.doFilter(request, response, (req, res) -> filter.doFilter(req, res, inner));
    }
}
