package com.telemed.notifications.adapter.in.http;

import java.util.List;

/**
 * Common error envelope (NORMA 5.3): {error, message, details, traceId}.
 */
public record ApiErrorResponse(
        String error,
        String message,
        List<FieldError> details,
        String traceId
) {

    public record FieldError(String field, String message) {
    }

    public static ApiErrorResponse of(
            String error,
            String message,
            String traceId
    ) {
        return new ApiErrorResponse(error, message, List.of(), traceId);
    }
}
