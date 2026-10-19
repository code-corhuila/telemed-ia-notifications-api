package com.telemed.notifications.adapter.in.http;

public final class CorrelationContext {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE = "traceId";

    private CorrelationContext() {
    }
}
