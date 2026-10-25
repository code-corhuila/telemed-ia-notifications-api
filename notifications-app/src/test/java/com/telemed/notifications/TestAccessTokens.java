package com.telemed.notifications;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** One test key pair per run: its public key is handed to the service as security.jwt.public-key. */
final class TestAccessTokens {

    private static final KeyPair KEY_PAIR = generate();

    private TestAccessTokens() {
    }

    static String publicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(KEY_PAIR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    static String accessToken(UUID userId, String role) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("role", role)
                    .issuer("telemed-ia-identity-and-access")
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plus(Duration.ofMinutes(10))))
                    .build());
            jwt.sign(new RSASSASigner(KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
