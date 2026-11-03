package com.telemed.notifications;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 11 traces (CP3): the inbox request spans go to the platform collector (and from there to
 * Jaeger) with the ids the logs and the response carry.
 */
@AutoConfigureObservability
@ExtendWith(OutputCaptureExtension.class)
@Import(ExportedSpans.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=dev",
                "management.server.port=0",
                "management.otlp.tracing.export.enabled=false"
        }
)
class SpanExportTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String CALLER_SPAN = "00f067aa0ba902b7";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.4-alpine")
                    .withDatabaseName("telemed_ia")
                    .withUsername("postgres")
                    .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        OfficialDatabaseSchemaInitializer.migrate(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password", () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
        registry.add("security.jwt.public-key", TestAccessTokens::publicKeyPem);
    }

    @Autowired
    private TestRestTemplate http;
    @Autowired
    private InMemorySpanExporter spans;
    @Autowired
    private ObjectProvider<SdkTracerProvider> tracerProvider;

    @LocalServerPort
    private int port;

    @BeforeEach
    void forgetEarlierSpans() {
        spans.reset();
    }

    @Test
    void theRequestSpanContinuesTheCallersTraceWithTheIdsOfTheLogsAndTheResponse(CapturedOutput output)
            throws InterruptedException {
        ResponseEntity<String> response = unreadCount("00-" + TRACE_ID + "-" + CALLER_SPAN + "-01");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<SpanData> request = ExportedSpans.await(spans, tracerProvider,
                span -> span.getTraceId().equals(TRACE_ID) && span.getParentSpanContext().isRemote());
        assertThat(request).singleElement().satisfies(span -> {
            assertThat(span.getParentSpanId()).isEqualTo(CALLER_SPAN);
            assertThat(response.getHeaders().getFirst("traceparent"))
                    .isEqualTo("00-" + TRACE_ID + "-" + span.getSpanId() + "-01");
            assertThat(output.getOut()).contains("\"span_id\":\"" + span.getSpanId() + "\"");
        });
    }

    @Test
    void aRequestWithoutTraceStartsOneThatIsExportedToo() throws InterruptedException {
        ResponseEntity<String> response = unreadCount(null);

        String traceId = response.getHeaders().getFirst("traceparent").split("-")[1];
        assertThat(ExportedSpans.await(spans, tracerProvider, span -> span.getTraceId().equals(traceId)))
                .isNotEmpty();
    }

    private ResponseEntity<String> unreadCount(String traceparent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestAccessTokens.accessToken(UUID.randomUUID(), "PATIENT"));
        if (traceparent != null) {
            headers.set("traceparent", traceparent);
        }
        return http.exchange("http://localhost:" + port + "/api/v1/notifications/unread-count",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
