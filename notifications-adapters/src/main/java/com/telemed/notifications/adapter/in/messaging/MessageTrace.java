package com.telemed.notifications.adapter.in.messaging;

import com.telemed.notifications.adapter.in.http.CorrelationContext;
import com.telemed.notifications.adapter.observability.CurrentTrace;
import com.telemed.notifications.adapter.observability.TraceContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

import java.time.Duration;

/**
 * Week 11 trace map (CP1): a consumed message continues the trace of the request that published
 * it, read from its W3C traceparent header, with a span of its own. Every message ends with one
 * message.consumed line: processed, rejected (dead-lettered) or failed (the broker retries it).
 * Bodies are never logged: they carry e-mail addresses and reset links.
 *
 * <p>Week 11 metric catalog (CP4): notifications_messages_consumed_total{queue, outcome} and
 * notifications_message_duration_seconds{queue}, recorded to the global registry, which Spring
 * Boot joins its Prometheus registry to.
 */
final class MessageTrace {

    private static final Logger LOG = LoggerFactory.getLogger(MessageTrace.class);
    // An e-mail is one SMTP round trip (about 1 s); the reminders read identity first.
    private static final Duration[] BUCKETS = {
            Duration.ofMillis(100), Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1),
            Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10)};

    private MessageTrace() {
    }

    static void consume(String queue, Message message, Runnable handling) {
        Object traceparent = message.getMessageProperties().getHeader(TraceContext.HEADER);
        TraceContext trace = TraceContext.from(traceparent instanceof String value ? value : null);
        MDC.put(CurrentTrace.TRACE_ID, trace.traceId());
        MDC.put(CurrentTrace.SPAN_ID, trace.spanId());
        correlate(message.getMessageProperties().getCorrelationId());
        long started = System.nanoTime();
        try {
            handling.run();
            record(queue, "processed", started);
            log(LOG.atInfo(), queue, "processed", started).log("message processed");
        } catch (AmqpRejectAndDontRequeueException rejected) {
            record(queue, "rejected", started);
            log(LOG.atWarn(), queue, "rejected", started).addKeyValue("reason", rejected.getMessage())
                    .log("message rejected to the dead-letter queue");
            throw rejected;
        } catch (RuntimeException failure) {
            record(queue, "failed", started);
            log(LOG.atError(), queue, "failed", started).addKeyValue("error_type", failure.getClass().getName())
                    .log("message failed; the broker retries it");
            throw failure;
        } finally {
            MDC.remove(CurrentTrace.TRACE_ID);
            MDC.remove(CurrentTrace.SPAN_ID);
            MDC.remove(CurrentTrace.CORRELATION_ID);
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }

    /** The event's correlation id, once the envelope is read; also the existing log traceId. */
    static void correlate(String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            return;
        }
        MDC.put(CurrentTrace.CORRELATION_ID, correlationId);
        MDC.put(CorrelationContext.MDC_KEY, correlationId);
    }

    private static void record(String queue, String outcome, long started) {
        Counter.builder("notifications.messages.consumed")
                .description("Consumed broker messages by outcome")
                .tags("queue", queue, "outcome", outcome)
                .register(Metrics.globalRegistry)
                .increment();
        Timer.builder("notifications.message.duration")
                .description("Time to handle a consumed message, e-mail included")
                .tags("queue", queue)
                .serviceLevelObjectives(BUCKETS)
                .register(Metrics.globalRegistry)
                .record(Duration.ofNanos(System.nanoTime() - started));
    }

    private static LoggingEventBuilder log(LoggingEventBuilder builder, String queue,
            String outcome, long started) {
        return builder.addKeyValue("event", "message.consumed")
                .addKeyValue("queue", queue)
                .addKeyValue("outcome", outcome)
                .addKeyValue("duration_ms", (System.nanoTime() - started) / 1_000_000);
    }
}
