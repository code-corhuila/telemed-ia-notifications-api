package com.telemed.notifications.adapter.in.http.observability;

import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.adapter.observability.CurrentTrace;
import com.telemed.notifications.adapter.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Week 11 log contract (CP2): right after CorrelationIdFilter, and before authentication, every
 * request gets its W3C trace context in the MDC, so each log line carries trace_id, span_id and
 * correlation_id. Only route templates are logged, never raw paths, bodies or recipients.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TraceContextFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TraceContext trace = TraceContext.from(request.getHeader(TraceContext.HEADER));
        Object correlationId = request.getAttribute(CorrelationContext.REQUEST_ATTRIBUTE);
        MDC.put(CurrentTrace.TRACE_ID, trace.traceId());
        MDC.put(CurrentTrace.SPAN_ID, trace.spanId());
        if (correlationId instanceof String value) {
            MDC.put(CurrentTrace.CORRELATION_ID, value);
        }
        response.setHeader(TraceContext.HEADER, trace.toHeader());
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            // A 5xx failed the reader and needs attention; a 4xx was the caller's and was answered.
            (response.getStatus() >= 500 ? LOG.atError() : LOG.atInfo())
                    .addKeyValue("event", "http.request.completed")
                    .addKeyValue("route", route == null ? "unmatched" : route.toString())
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("duration_ms", (System.nanoTime() - started) / 1_000_000)
                    .log("request completed");
            MDC.remove(CurrentTrace.TRACE_ID);
            MDC.remove(CurrentTrace.SPAN_ID);
            MDC.remove(CurrentTrace.CORRELATION_ID);
        }
    }
}
