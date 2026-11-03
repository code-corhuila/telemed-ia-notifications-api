package com.telemed.notifications.adapter.in.messaging;

import com.telemed.notifications.adapter.CapturedLogs;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Week 11 trace map (CP1): the trace of the request that published an event continues here. */
class MessageTraceTest {

    private static final String PUBLISHER_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String QUEUE = "notifications.password-recovery-requested";

    private final Map<String, String> seenByTheHandler = new HashMap<>();

    @Test
    void continuesThePublishersTraceWithItsOwnSpan() {
        Message message = message("00-" + PUBLISHER_TRACE + "-00f067aa0ba902b7-01", "corr-0001");

        MessageTrace.consume(QUEUE, message, () -> seenByTheHandler.putAll(MDC.getCopyOfContextMap()));

        assertThat(seenByTheHandler).containsEntry("trace_id", PUBLISHER_TRACE)
                .containsEntry("correlation_id", "corr-0001")
                .containsEntry("traceId", "corr-0001");
        assertThat(seenByTheHandler.get("span_id")).matches("[0-9a-f]{16}").isNotEqualTo("00f067aa0ba902b7");
    }

    @Test
    void startsANewTraceWhenTheMessageCarriesNone() {
        MessageTrace.consume(QUEUE, message(null, null), () -> seenByTheHandler.putAll(MDC.getCopyOfContextMap()));

        assertThat(seenByTheHandler.get("trace_id")).matches("[0-9a-f]{32}");
        assertThat(seenByTheHandler).doesNotContainKey("correlation_id");
    }

    @Test
    void correlateUsesTheEnvelopesCorrelationId() {
        MessageTrace.consume(QUEUE, message(null, null), () -> {
            MessageTrace.correlate("corr-from-envelope");
            seenByTheHandler.putAll(MDC.getCopyOfContextMap());
        });

        assertThat(seenByTheHandler).containsEntry("correlation_id", "corr-from-envelope")
                .containsEntry("traceId", "corr-from-envelope");
    }

    @Test
    void logsTheOutcomeOfEveryMessage() {
        try (CapturedLogs logs = CapturedLogs.of(MessageTrace.class)) {
            MessageTrace.consume(QUEUE, message(null, null), () -> { });
            assertThatThrownBy(() -> MessageTrace.consume(QUEUE, message(null, null), () -> {
                throw new AmqpRejectAndDontRequeueException("Unsupported event type or version.");
            })).isInstanceOf(AmqpRejectAndDontRequeueException.class);
            assertThatThrownBy(() -> MessageTrace.consume(QUEUE, message(null, null), () -> {
                throw new IllegalStateException("SMTP unavailable");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(logs.events("message.consumed"))
                    .extracting(event -> event.get("level"), event -> event.get("outcome"), event -> event.get("queue"))
                    .containsExactly(tuple("INFO", "processed", QUEUE), tuple("WARN", "rejected", QUEUE),
                            tuple("ERROR", "failed", QUEUE));
            assertThat(logs.events("message.consumed")).allSatisfy(event -> assertThat(event).containsKey("duration_ms"));
        }
    }

    @Test
    void leavesNothingBehindForTheNextMessageOnTheSameThread() {
        MessageTrace.consume(QUEUE, message("00-" + PUBLISHER_TRACE + "-00f067aa0ba902b7-01", "corr-0001"), () -> { });

        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("correlation_id")).isNull();
        assertThat(MDC.get("traceId")).isNull();
    }

    private static Message message(String traceparent, String correlationId) {
        MessageProperties properties = new MessageProperties();
        if (traceparent != null) {
            properties.setHeader("traceparent", traceparent);
        }
        properties.setCorrelationId(correlationId);
        return new Message("{}".getBytes(), properties);
    }
}
