package com.telemed.notifications.adapter.out.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads identity's public key the same way identity-api does (inline PEM or file). */
public final class RsaPublicKeyLoader {

    private RsaPublicKeyLoader() {
    }

    public static RSAPublicKey load(String inline, String file) {
        String body = resolvePem(inline, file)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(body);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("JWT public key must be a valid X.509 RSA PEM.", exception);
        }
    }

    private static String resolvePem(String inline, String file) {
        if (inline != null && !inline.isBlank()) {
            return inline.replace("\\n", "\n");
        }
        if (file != null && !file.isBlank()) {
            try {
                return Files.readString(Path.of(file), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Unable to read JWT public key file.", exception);
            }
        }
        throw new IllegalArgumentException(
                "JWT public key must be supplied inline or through a key file."
        );
    }
}
