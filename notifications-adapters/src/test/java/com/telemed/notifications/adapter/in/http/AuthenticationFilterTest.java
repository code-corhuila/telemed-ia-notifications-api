package com.telemed.notifications.adapter.in.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemed.notifications.adapter.out.security.JwtAccessTokenVerifier;
import com.telemed.notifications.adapter.out.security.TestTokens;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationFilterTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private final TestTokens tokens = new TestTokens();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuthenticationFilter filter = new AuthenticationFilter(
            new JwtAccessTokenVerifier(tokens.publicKey(), TestTokens.ISSUER),
            objectMapper
    );

    @Test
    void shouldLetAValidTokenThroughWithItsUser() throws Exception {

        MockHttpServletRequest request = request();
        request.addHeader("Authorization", "Bearer " + tokens.accessToken(USER_ID, "PATIENT"));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(AuthenticationFilter.currentUser(request)).isEqualTo(new AuthenticatedUser(USER_ID, "PATIENT"));
    }

    @Test
    void shouldAnswer401WithTheCommonEnvelopeWithoutAToken() throws Exception {

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(), response, chain);

        assertUnauthorized(response, chain);
    }

    @Test
    void shouldAnswer401ForAnotherSchemeOrAnInvalidToken() throws Exception {

        MockHttpServletRequest basic = request();
        basic.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse basicResponse = new MockHttpServletResponse();
        MockFilterChain basicChain = new MockFilterChain();
        filter.doFilter(basic, basicResponse, basicChain);
        assertUnauthorized(basicResponse, basicChain);

        MockHttpServletRequest forged = request();
        forged.addHeader("Authorization", "Bearer " + new TestTokens().accessToken(USER_ID, "PATIENT"));
        MockHttpServletResponse forgedResponse = new MockHttpServletResponse();
        MockFilterChain forgedChain = new MockFilterChain();
        filter.doFilter(forged, forgedResponse, forgedChain);
        assertUnauthorized(forgedResponse, forgedChain);
    }

    private void assertUnauthorized(MockHttpServletResponse response, MockFilterChain chain) throws Exception {
        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/json");

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("message").asText()).isEqualTo("A valid access token is required.");
        assertThat(body.get("details").isArray()).isTrue();
        assertThat(body.get("traceId").asText()).isEqualTo("trace-123");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/notifications");
        request.setAttribute(CorrelationContext.REQUEST_ATTRIBUTE, "trace-123");
        return request;
    }
}
