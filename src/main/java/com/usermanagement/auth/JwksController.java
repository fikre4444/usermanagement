package com.usermanagement.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the token verification key. Other services validate access tokens with it, e.g. in
 * Spring Boot: {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://<host>/.well-known/jwks.json}.
 */
@RestController
@Tag(name = "Token keys")
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey jwtSigningKey) {
        this.jwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Public keys for verifying access tokens (JWKS)")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
