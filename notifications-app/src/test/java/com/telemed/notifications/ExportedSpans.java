package com.telemed.notifications;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Week 11 traces (CP3) in tests: the spans notifications hands to its exporter, kept in memory instead
 * of being sent to the platform collector.
 */
@TestConfiguration
class ExportedSpans {

    @Bean
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }

    /** Spans end after the response is written and are exported in batches, so they may lag briefly. */
    static List<SpanData> await(InMemorySpanExporter spans, ObjectProvider<SdkTracerProvider> tracer,
                                Predicate<SpanData> wanted) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            tracer.ifAvailable(provider -> provider.forceFlush().join(1, TimeUnit.SECONDS));
            List<SpanData> found = spans.getFinishedSpanItems().stream().filter(wanted).toList();
            if (!found.isEmpty()) {
                return found;
            }
            Thread.sleep(100);
        }
        return List.of();
    }
}
