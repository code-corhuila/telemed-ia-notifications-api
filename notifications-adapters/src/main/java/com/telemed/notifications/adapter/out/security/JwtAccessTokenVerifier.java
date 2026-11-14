package com.telemed.notifications.adapter.out.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.telemed.notifications.application.exception.AccessTokenVerificationException;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Verifies identity's access tokens locally (NORMA: RS256 validated by every
 * service). Service tokens are rejected: their subject is not a user.
 */
public class JwtAccessTokenVerifier implements AccessTokenVerifierPort {

    private final String issuer;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;

    public JwtAccessTokenVerifier(RSAPublicKey publicKey, String issuer) {
        this(publicKey, issuer, Clock.systemUTC());
    }

    JwtAccessTokenVerifier(RSAPublicKey publicKey, String issuer, Clock clock) {
        this.issuer = issuer;
        this.clock = clock;
        this.decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // The decoder only checks the signature; the claims are checked below.
        this.decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    @Override
    public AuthenticatedUser verify(String token) {

        if (token == null || token.isBlank()) {
            throw new AccessTokenVerificationException("Access token is required.");
        }

        requireRs256(token);

        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException exception) {
            throw new AccessTokenVerificationException("Access token signature is invalid.", exception);
        }

        return authenticatedUser(jwt);
    }

    private static void requireRs256(String token) {
        JWSAlgorithm algorithm;
        try {
            algorithm = SignedJWT.parse(token).getHeader().getAlgorithm();
        } catch (ParseException exception) {
            throw new AccessTokenVerificationException("Access token is malformed.", exception);
        }
        if (!JWSAlgorithm.RS256.equals(algorithm)) {
            throw new AccessTokenVerificationException("Access token must use RS256.");
        }
    }

    private AuthenticatedUser authenticatedUser(Jwt jwt) {

        if (!issuer.equals(jwt.getClaimAsString("iss"))) {
            throw new AccessTokenVerificationException("Access token issuer is invalid.");
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !expiresAt.isAfter(clock.instant())) {
            throw new AccessTokenVerificationException("Access token has expired.");
        }

        if (isBlank(jwt.getId())) {
            throw new AccessTokenVerificationException("Access token requires jti claim.");
        }

        String role = jwt.getClaimAsString("role");
        if (isBlank(role)) {
            throw new AccessTokenVerificationException("Access token requires role claim.");
        }

        String subject = jwt.getSubject();
        if (isBlank(subject)) {
            throw new AccessTokenVerificationException("Access token requires sub claim.");
        }

        try {
            return new AuthenticatedUser(UUID.fromString(subject), role);
        } catch (IllegalArgumentException exception) {
            throw new AccessTokenVerificationException("Access token subject is not a user.", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
