package com.telemed.notifications.adapter.out.security;

import com.nimbusds.jwt.JWTClaimsSet;
import com.telemed.notifications.application.exception.AccessTokenVerificationException;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.telemed.notifications.adapter.out.security.TestTokens.ISSUER;
import static com.telemed.notifications.adapter.out.security.TestTokens.claims;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** NORMA: every service validates identity's RS256 access token itself. */
class JwtAccessTokenVerifierTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final Instant IN_TEN_MINUTES = Instant.now().plus(Duration.ofMinutes(10));

    private final TestTokens tokens = new TestTokens();

    private final JwtAccessTokenVerifier verifier = new JwtAccessTokenVerifier(tokens.publicKey(), ISSUER);

    @Test
    void shouldIdentifyTheUserOfAValidToken() {

        AuthenticatedUser user = verifier.verify(tokens.accessToken(USER_ID, "PATIENT"));

        assertThat(user).isEqualTo(new AuthenticatedUser(USER_ID, "PATIENT"));
    }

    @Test
    void shouldRejectATokenSignedWithAnotherKey() {

        String forged = new TestTokens().accessToken(USER_ID, "PATIENT");

        assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectAnotherAlgorithm() {

        String hs256 = TestTokens.signHs256(claims(USER_ID.toString(), "PATIENT", ISSUER, IN_TEN_MINUTES));

        assertThatThrownBy(() -> verifier.verify(hs256)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectAnExpiredToken() {

        String expired = tokens.sign(claims(USER_ID.toString(), "PATIENT", ISSUER, Instant.now().minus(Duration.ofMinutes(5))));

        assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectAnotherIssuer() {

        String foreign = tokens.sign(claims(USER_ID.toString(), "PATIENT", "someone-else", IN_TEN_MINUTES));

        assertThatThrownBy(() -> verifier.verify(foreign)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectAServiceTokenBecauseItsSubjectIsNotAUser() {

        String service = tokens.sign(claims("worker", "SERVICE", ISSUER, IN_TEN_MINUTES));

        assertThatThrownBy(() -> verifier.verify(service)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectATokenWithoutRole() {

        String roleless = tokens.sign(claims(USER_ID.toString(), null, ISSUER, IN_TEN_MINUTES));

        assertThatThrownBy(() -> verifier.verify(roleless)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectATokenWithoutId() {

        String anonymous = tokens.sign(new JWTClaimsSet.Builder(
                claims(USER_ID.toString(), "PATIENT", ISSUER, IN_TEN_MINUTES)).jwtID(null).build());

        assertThatThrownBy(() -> verifier.verify(anonymous)).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldRejectGarbage() {

        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).isInstanceOf(AccessTokenVerificationException.class);
        assertThatThrownBy(() -> verifier.verify(" ")).isInstanceOf(AccessTokenVerificationException.class);
    }

    @Test
    void shouldLoadThePublicKeyFromAPemWithEscapedNewLines() {

        String escaped = tokens.publicKeyPem().replace("\n", "\\n");

        JwtAccessTokenVerifier fromPem = new JwtAccessTokenVerifier(RsaPublicKeyLoader.load(escaped, null), ISSUER);

        assertThat(fromPem.verify(tokens.accessToken(USER_ID, "PROFESSIONAL")).role()).isEqualTo("PROFESSIONAL");
    }

    @Test
    void shouldRequireAPublicKey() {

        assertThatThrownBy(() -> RsaPublicKeyLoader.load(" ", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
