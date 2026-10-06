package com.usermanagement.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Token settings. Keys are PEM encoded RSA keys (PKCS#8 private key, X.509 public key) given either
 * inline or as a resource location such as {@code file:/run/secrets/jwt-private.pem}.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @DefaultValue("user-management-service") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        String privateKey,
        String publicKey,
        /* Optional explicit key id; defaults to the key's RFC 7638 thumbprint. */
        String keyId,
        /* Generate a throw-away key pair when none is configured (development only). */
        @DefaultValue("true") boolean allowGeneratedKey) {
}
