package com.telemed.notifications.adapter.out.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param issuer        issuer identity writes into its access tokens
 * @param publicKey     identity's RS256 public key as PEM, with escaped or real new lines
 * @param publicKeyFile path to that PEM, used when publicKey is empty
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String issuer,
        String publicKey,
        String publicKeyFile
) {

    public JwtProperties {

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException(
                    "security.jwt.issuer is required."
            );
        }
    }
}
