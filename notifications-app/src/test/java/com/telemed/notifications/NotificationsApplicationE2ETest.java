package com.telemed.notifications;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=dev",
                "management.server.port=0"
        }
)
class NotificationsApplicationE2ETest {

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
        registry.add("spring.datasource.username",
                () -> OfficialDatabaseSchemaInitializer.APP_USER);
        registry.add("spring.datasource.password",
                () -> OfficialDatabaseSchemaInitializer.APP_PASSWORD);
    }

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReportHealthyWithDatabaseAsNotificationsApp()
            throws Exception {
        ResponseEntity<String> response =
                http.getForEntity(
                        "http://localhost:" + managementPort + "/actuator/health",
                        String.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody())
                .get("status").asText()).isEqualTo("UP");
    }

    @Test
    void shouldAnswerUnknownRoutesWithCommonErrorEnvelope()
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", "e2e-trace-1");

        ResponseEntity<String> response =
                http.exchange(
                        "http://localhost:" + port + "/api/v1/notifications/unknown",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        String.class
                );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getFirst("X-Correlation-Id"))
                .isEqualTo("e2e-trace-1");

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("error").asText()).isEqualTo("NOT_FOUND");
        assertThat(body.get("traceId").asText()).isEqualTo("e2e-trace-1");
        assertThat(body.has("message")).isTrue();
    }
}
