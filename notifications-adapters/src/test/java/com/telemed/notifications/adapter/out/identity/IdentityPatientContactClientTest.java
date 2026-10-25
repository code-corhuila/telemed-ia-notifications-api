package com.telemed.notifications.adapter.out.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.telemed.notifications.application.exception.PatientContactUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The patient's e-mail comes from identity, asked with notifications' own service token. */
class IdentityPatientContactClientTest {

    private static final UUID PATIENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SECRET = "notifications-test-secret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger tokensIssued = new AtomicInteger();
    private final List<String> tokenRequests = new CopyOnWriteArrayList<>();
    private final List<String> lookupAuthorizations = new CopyOnWriteArrayList<>();

    private HttpServer identity;
    private volatile int recipientStatus = 200;
    private volatile int rejectTokensBelow = 0;
    private volatile Duration tokenLifetime = Duration.ofMinutes(10);
    private volatile Duration lookupDelay = Duration.ZERO;

    @BeforeEach
    void startIdentity() throws IOException {
        identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identity.createContext("/internal/service-token", exchange -> {
            tokenRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int issued = tokensIssued.incrementAndGet();
            respond(exchange, 200, Map.of(
                    "accessToken", "service-token-" + issued,
                    "tokenType", "Bearer",
                    "expiresAt", Instant.now().plus(tokenLifetime).toString()));
        });
        identity.createContext("/internal/notification-recipients/", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            lookupAuthorizations.add(authorization);
            sleep(lookupDelay);
            int tokenNumber = Integer.parseInt(authorization.substring("Bearer service-token-".length()));
            if (tokenNumber <= rejectTokensBelow) {
                respond(exchange, 401, Map.of("error", "UNAUTHORIZED"));
            } else if (recipientStatus != 200) {
                respond(exchange, recipientStatus, Map.of("error", "NOT_FOUND"));
            } else {
                String id = exchange.getRequestURI().getPath().substring("/internal/notification-recipients/".length());
                respond(exchange, 200, Map.of("id", id, "email", "laura@telemed.co"));
            }
        });
        identity.start();
    }

    @AfterEach
    void stopIdentity() {
        identity.stop(0);
    }

    @Test
    void answersThePatientsEmailAskedWithAServiceToken() throws Exception {

        assertThat(client(Duration.ofSeconds(3)).findEmail(PATIENT_ID)).contains("laura@telemed.co");

        assertThat(objectMapper.readTree(tokenRequests.get(0)).get("clientId").asText()).isEqualTo("notifications");
        assertThat(objectMapper.readTree(tokenRequests.get(0)).get("clientSecret").asText()).isEqualTo(SECRET);
        assertThat(lookupAuthorizations).containsExactly("Bearer service-token-1");
    }

    @Test
    void reusesTheServiceTokenUntilItIsAboutToExpire() {
        IdentityPatientContactClient client = client(Duration.ofSeconds(3));

        client.findEmail(PATIENT_ID);
        client.findEmail(PATIENT_ID);
        assertThat(tokensIssued).hasValue(1);

        tokenLifetime = Duration.ofSeconds(5);
        IdentityPatientContactClient shortLived = client(Duration.ofSeconds(3));
        shortLived.findEmail(PATIENT_ID);
        shortLived.findEmail(PATIENT_ID);
        assertThat(tokensIssued).hasValue(3);
    }

    @Test
    void answersNothingForAnAccountThatIsNotAnActivePatient() {
        recipientStatus = 404;

        assertThat(client(Duration.ofSeconds(3)).findEmail(PATIENT_ID)).isEmpty();
    }

    @Test
    void asksForANewTokenOnceWhenIdentityRejectsTheCachedOne() {
        IdentityPatientContactClient client = client(Duration.ofSeconds(3));
        client.findEmail(PATIENT_ID);

        rejectTokensBelow = 1;

        assertThat(client.findEmail(PATIENT_ID)).contains("laura@telemed.co");
        assertThat(lookupAuthorizations).containsExactly(
                "Bearer service-token-1", "Bearer service-token-1", "Bearer service-token-2");
    }

    @Test
    void failsWhenIdentityKeepsRejectingTheToken() {
        rejectTokensBelow = Integer.MAX_VALUE;

        assertThatThrownBy(() -> client(Duration.ofSeconds(3)).findEmail(PATIENT_ID))
                .isInstanceOf(PatientContactUnavailableException.class);
        assertThat(tokensIssued).hasValue(2);
    }

    @Test
    void failsWhenIdentityAnswersAnError() {
        recipientStatus = 503;

        assertThatThrownBy(() -> client(Duration.ofSeconds(3)).findEmail(PATIENT_ID))
                .isInstanceOf(PatientContactUnavailableException.class);
    }

    @Test
    void failsWhenIdentityIsTooSlow() {
        lookupDelay = Duration.ofSeconds(2);

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).findEmail(PATIENT_ID))
                .isInstanceOf(PatientContactUnavailableException.class);
    }

    @Test
    void failsWhenIdentityIsDownWithoutRevealingTheSecret() {
        identity.stop(0);

        assertThatThrownBy(() -> client(Duration.ofSeconds(3)).findEmail(PATIENT_ID))
                .isInstanceOf(PatientContactUnavailableException.class)
                .satisfies(error -> assertThat(String.valueOf(error.getMessage())).doesNotContain(SECRET));
    }

    @Test
    void needsACompleteConfiguration() {
        assertThatThrownBy(() -> new IdentityClientProperties("http://identity", "notifications", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityClientProperties(" ", "notifications", SECRET, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new IdentityClientProperties("http://identity", "notifications", SECRET, null).timeout())
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(new IdentityClientProperties("http://identity", "notifications", SECRET, null).toString())
                .doesNotContain(SECRET);
    }

    private IdentityPatientContactClient client(Duration timeout) {
        return new IdentityPatientContactClient(new IdentityClientProperties(
                "http://127.0.0.1:" + identity.getAddress().getPort(), "notifications", SECRET, timeout));
    }

    private void respond(HttpExchange exchange, int status, Map<String, String> body) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
