package com.usermanagement.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.usermanagement.extension.TokenClaimsCustomizer;
import com.usermanagement.user.User;
import com.usermanagement.user.UserSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived RS256 access tokens. Claims:
 * {@code sub} (user id), {@code user_type}, {@code roles}, {@code permissions}, and the standard
 * OIDC-style {@code preferred_username}, {@code email}, {@code email_verified}, {@code phone_number},
 * {@code phone_number_verified} when available.
 */
@Service
public class TokenService {

    private static final Set<String> RESERVED_CLAIMS =
            Set.of("sub", "iss", "exp", "iat", "nbf", "jti", "aud", "roles", "permissions", "user_type");

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final String keyId;
    private final List<TokenClaimsCustomizer> customizers;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, JwtProperties properties, RSAKey jwtSigningKey,
                        List<TokenClaimsCustomizer> customizers, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.keyId = jwtSigningKey.getKeyID();
        this.customizers = customizers;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTokenTtl());

        Map<String, Object> extra = new LinkedHashMap<>();
        if (user.getUsername() != null) {
            extra.put("preferred_username", user.getUsername());
        }
        if (user.getEmail() != null) {
            extra.put("email", user.getEmail());
            extra.put("email_verified", user.isEmailVerified());
        }
        if (user.getPhone() != null) {
            extra.put("phone_number", user.getPhone());
            extra.put("phone_number_verified", user.isPhoneVerified());
        }
        if (!customizers.isEmpty()) {
            UserSnapshot snapshot = UserSnapshot.of(user);
            customizers.forEach(customizer -> customizer.customize(snapshot, extra));
        }
        RESERVED_CLAIMS.forEach(extra::remove);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("user_type", user.getUserType())
                .claim("roles", List.copyOf(user.roleNames()))
                .claim("permissions", List.copyOf(user.permissions()))
                .claims(existing -> existing.putAll(extra))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

    public record AccessToken(String value, Instant expiresAt) {
    }
}
