package com.telemed.notifications.adapter.in.http;

import com.telemed.notifications.application.exception.InvalidInboxQueryException;
import com.telemed.notifications.application.exception.NotificationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        "NOT_FOUND",
                        "The requested resource does not exist.",
                        traceId(request)
                ));
    }

    @ExceptionHandler(InvalidInboxQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidQuery(
            InvalidInboxQueryException exception,
            HttpServletRequest request
    ) {
        return validationError(exception.field(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return validationError(exception.getName(), exception.getName() + " has an invalid format.", request);
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotificationNotFound(
            NotificationNotFoundException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        "NOT_FOUND",
                        exception.getMessage(),
                        traceId(request)
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(exception.getHeaders())
                .body(ApiErrorResponse.of(
                        "METHOD_NOT_ALLOWED",
                        "The HTTP method is not supported for this path.",
                        traceId(request)
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected error while processing request.", exception);

        // Never expose driver messages or stack traces to the client.
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred.",
                        traceId(request)
                ));
    }

    private static ResponseEntity<ApiErrorResponse> validationError(
            String field,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "The request has invalid parameters.",
                        List.of(new ApiErrorResponse.FieldError(field, message)),
                        traceId(request)
                ));
    }

    private static String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute(
                CorrelationContext.REQUEST_ATTRIBUTE
        );

        return traceId instanceof String value ? value : null;
    }
}
