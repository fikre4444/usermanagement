package com.usermanagement.config;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the authenticated user's id from the access token. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
