package com.telemed.notifications;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/** Week 11 metric catalog (CP4): Prometheus scrapes notifications on the management port only. */
// Spring Boot turns metrics export off in tests unless asked for.
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=dev",
                "management.server.port=0",
                // Tracing is on here too: keep the spans off the platform collector, absent in tests.
                "management.otlp.tracing.export.enabled=false"
        }
)
class MetricsEndpointE2ETest {

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

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Test
    void exposesTheWeek11MetricsOnTheManagementPort() throws InterruptedException {
        // No access token: answered 401 by the authentication filter, and counted.
        http.getForEntity("http://localhost:" + port + "/api/v1/notifications", String.class);

        ResponseEntity<String> scrape = scrapeOnceRecorded("notifications_requests_total{");

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(scrape.getBody())
                .containsPattern("notifications_requests_total\\{[^}]*outcome=\"client_error\"")
                .containsPattern("notifications_request_duration_seconds_bucket\\{[^}]*le=\"0.5\"")
                // Saturation: the connection pool to notifications-db.
                .contains("hikaricp_connections_active{");
    }

    @Test
    void recordsTheConsumedMessagesInTheScrapedRegistry() {
        // MessageTrace records to the global registry; the scraped one must be part of it.
        assertThat(Metrics.globalRegistry.getRegistries())
                .anyMatch(registry -> registry instanceof PrometheusMeterRegistry);
    }

    @Test
    void keepsTheMetricsOffTheApiPort() {
        ResponseEntity<String> response =
                http.getForEntity("http://localhost:" + port + "/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> scrape() {
        return http.getForEntity("http://localhost:" + managementPort + "/actuator/prometheus", String.class);
    }

    /**
     * The filter records a request after its response is sent, so a fast scrape can arrive
     * first; Prometheus scrapes every few seconds and never notices, a test can.
     */
    private ResponseEntity<String> scrapeOnceRecorded(String series) throws InterruptedException {
        ResponseEntity<String> scrape = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            scrape = scrape();
            if (scrape.getBody() != null && scrape.getBody().contains(series)) {
                return scrape;
            }
            Thread.sleep(100);
        }
        return scrape;
    }
}
