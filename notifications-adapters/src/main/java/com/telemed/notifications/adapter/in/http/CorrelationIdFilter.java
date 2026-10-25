package com.telemed.notifications.adapter.in.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Reuses a safe incoming X-Correlation-Id or generates one, returns it and
 * exposes it as the log traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String incomingId =
                request.getHeader(CorrelationContext.HEADER);

        String traceId = incomingId != null
                && SAFE_ID.matcher(incomingId).matches()
                ? incomingId
                : UUID.randomUUID().toString();

        String previousTraceId =
                MDC.get(CorrelationContext.MDC_KEY);

        MDC.put(CorrelationContext.MDC_KEY, traceId);
        request.setAttribute(CorrelationContext.REQUEST_ATTRIBUTE, traceId);
        response.setHeader(CorrelationContext.HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousTraceId == null) {
                MDC.remove(CorrelationContext.MDC_KEY);
            } else {
                MDC.put(CorrelationContext.MDC_KEY, previousTraceId);
            }
        }
    }
}
