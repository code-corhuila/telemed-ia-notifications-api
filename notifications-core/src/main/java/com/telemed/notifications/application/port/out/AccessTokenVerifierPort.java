package com.telemed.notifications.application.port.out;

import java.util.UUID;

/** Verifies an access token issued by identity and tells who sent the request. */
public interface AccessTokenVerifierPort {

    AuthenticatedUser verify(String token);

    record AuthenticatedUser(UUID userId, String role) {
    }
}
