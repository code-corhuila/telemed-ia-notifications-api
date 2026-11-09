package com.telemed.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 11 runbook (CP8): when notifications-db does not answer, the inbox fails fast with a 503
 * instead of holding the request and answering 500.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=dev",
                "management.server.port=0",
                // Short waits so the test is quick; the defaults are 5 s and 3 s.
                "spring.datasource.hikari.connection-timeout=500",
                "spring.datasource.hikari.validation-timeout=250"
        }
)
class DatabaseFailFastE2ETest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @LocalServerPort
    private int port;

    @Test
    void boundsEveryWaitOnNotificationsDb() {
        // A stalled server holds a read forever unless the driver has a socket timeout.
        assertThat(((HikariDataSource) dataSource).getDataSourceProperties())
                .containsEntry("socketTimeout", "10")
                .containsEntry("connectTimeout", "3")
                .containsEntry("options", "-c statement_timeout=5000");
    }

    @Test
    void answersA503QuicklyWhenNotificationsDbIsDown() throws Exception {
        POSTGRES.stop();

        long started = System.nanoTime();
        ResponseEntity<String> response = unreadCount();
        Duration took = Duration.ofNanos(System.nanoTime() - started);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("error").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(body.get("traceId").asText()).isNotBlank();
        assertThat(took).isLessThan(Duration.ofSeconds(5));
    }

    private ResponseEntity<String> unreadCount() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TestAccessTokens.accessToken(UUID.randomUUID(), "PATIENT"));
        return http.exchange("http://localhost:" + port + "/api/v1/notifications/unread-count",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
