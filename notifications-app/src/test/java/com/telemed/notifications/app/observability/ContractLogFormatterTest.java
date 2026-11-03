package com.telemed.notifications.app.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Week 11 log contract (CP2, docs/observability/log-contract.md): one JSON object per line. */
class ContractLogFormatterTest {

    private final ContractLogFormatter formatter = new ContractLogFormatter("0.1.0", "dev");

    @Test
    void theProdProfileDeploysWithTheContractFormatToo() throws IOException {
        // deploy/compose.yml runs the prod profile: its settings take precedence over application.yml.
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader yaml = new YamlPropertySourceLoader();
        yaml.load("prod", new ClassPathResource("application-prod.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        yaml.load("default", new ClassPathResource("application.yml"))
                .forEach(source -> environment.getPropertySources().addLast(source));

        assertThat(environment.getProperty("logging.structured.format.console"))
                .isEqualTo(ContractLogFormatter.class.getName());
    }

    @Test
    void writesEveryMandatoryFieldOfTheContract() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.INFO);
        event.setTimeStamp(1_792_768_801_120L);
        event.setMessage("password recovery e-mail sent");
        event.setMDCPropertyMap(Map.of("trace_id", "7d3f9a2c4b1e8f60a9c2d5e7f1b3a4c6",
                "span_id", "2b3c4d5e6f708192", "correlation_id", "corr-0001", "traceId", "corr-0001"));
        event.setKeyValuePairs(List.of(new KeyValuePair("event", "notification.email.sent"),
                new KeyValuePair("duration_ms", 38)));

        String line = formatter.format(event);
        JsonNode json = new ObjectMapper().readTree(line);

        assertThat(line).endsWith("\n");
        assertThat(json.get("timestamp").asText()).isEqualTo("2026-10-23T15:20:01.120Z");
        assertThat(json.get("level").asText()).isEqualTo("INFO");
        assertThat(json.get("service").asText()).isEqualTo("notifications-api");
        assertThat(json.get("version").asText()).isEqualTo("0.1.0");
        assertThat(json.get("environment").asText()).isEqualTo("dev");
        assertThat(json.get("trace_id").asText()).isEqualTo("7d3f9a2c4b1e8f60a9c2d5e7f1b3a4c6");
        assertThat(json.get("span_id").asText()).isEqualTo("2b3c4d5e6f708192");
        assertThat(json.get("correlation_id").asText()).isEqualTo("corr-0001");
        assertThat(json.get("event").asText()).isEqualTo("notification.email.sent");
        assertThat(json.get("message").asText()).isEqualTo("password recovery e-mail sent");
        assertThat(json.get("duration_ms").asLong()).isEqualTo(38);
        // The MDC key the error envelope uses is not a contract field.
        assertThat(json.has("traceId")).isFalse();
    }

    @Test
    void namesPlainLogLinesAfterTheirLogger() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.WARN);
        event.setLoggerName("com.telemed.notifications.adapter.in.http.GlobalExceptionHandler");
        event.setMessage("Inbox request rejected.");
        event.setMDCPropertyMap(Map.of());

        JsonNode json = new ObjectMapper().readTree(formatter.format(event));

        assertThat(json.get("event").asText()).isEqualTo("log.GlobalExceptionHandler");
        assertThat(json.has("trace_id")).isTrue();
    }

    @Test
    void writesTheExceptionTypeAndStackOfAFailure() throws Exception {
        LoggingEvent event = new LoggingEvent();
        event.setLevel(Level.ERROR);
        event.setMessage("Unexpected error while processing request.");
        event.setThrowableProxy(new ThrowableProxy(new IllegalStateException("boom")));
        event.setMDCPropertyMap(Map.of());

        JsonNode json = new ObjectMapper().readTree(formatter.format(event));

        assertThat(json.get("error_type").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(json.get("stack_trace").asText()).contains("boom");
    }
}
