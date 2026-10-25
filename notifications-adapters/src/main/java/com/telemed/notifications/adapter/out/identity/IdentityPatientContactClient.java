package com.telemed.notifications.adapter.out.identity;

import com.telemed.notifications.application.exception.PatientContactUnavailableException;
import com.telemed.notifications.application.port.out.PatientContactPort;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GET /internal/notification-recipients/{id} with notifications' own service token from
 * POST /internal/service-token. The token is reused until shortly before it expires.
 */
public class IdentityPatientContactClient implements PatientContactPort {

    // A token this close to its expiry could expire on the way to identity.
    private static final Duration EXPIRY_MARGIN = Duration.ofSeconds(30);

    private final RestClient identity;
    private final IdentityClientProperties properties;

    private ServiceToken token;

    public IdentityPatientContactClient(IdentityClientProperties properties) {
        this.properties = properties;

        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build());
        requests.setReadTimeout(properties.timeout());

        this.identity = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requests)
                .build();
    }

    private record ServiceToken(String accessToken, Instant expiresAt) {
    }

    private record ServiceTokenResponse(String accessToken, String expiresAt) {
    }

    private record RecipientResponse(UUID id, String email) {
    }

    @Override
    public Optional<String> findEmail(UUID patientId) {
        try {
            try {
                return lookup(patientId, currentToken());

            } catch (HttpClientErrorException.Unauthorized rejected) {
                // Identity restarted with another key or no longer accepts the token: one fresh token, once.
                return lookup(patientId, newToken());
            }
        } catch (HttpClientErrorException.NotFound notAnActivePatient) {
            return Optional.empty();

        } catch (RestClientException | IllegalStateException failure) {
            throw new PatientContactUnavailableException("Identity could not answer the patient's e-mail.", failure);
        }
    }

    private Optional<String> lookup(UUID patientId, String accessToken) {
        RecipientResponse recipient = identity.get()
                .uri("/internal/notification-recipients/{id}", patientId)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(RecipientResponse.class);

        if (recipient == null || recipient.email() == null || recipient.email().isBlank()) {
            throw new IllegalStateException("Identity answered a recipient without e-mail.");
        }
        return Optional.of(recipient.email());
    }

    private synchronized String currentToken() {
        if (token == null || Instant.now().isAfter(token.expiresAt().minus(EXPIRY_MARGIN))) {
            return newToken();
        }
        return token.accessToken();
    }

    private synchronized String newToken() {
        ServiceTokenResponse issued = identity.post()
                .uri("/internal/service-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("clientId", properties.clientId(), "clientSecret", properties.clientSecret()))
                .retrieve()
                .body(ServiceTokenResponse.class);

        if (issued == null || issued.accessToken() == null || issued.expiresAt() == null) {
            throw new IllegalStateException("Identity answered no service token.");
        }
        token = new ServiceToken(issued.accessToken(), Instant.parse(issued.expiresAt()));
        return token.accessToken();
    }
}
