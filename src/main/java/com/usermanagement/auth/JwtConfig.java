package com.usermanagement.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** RS256 signing key, JWT encoder and decoder. The public key is published at /.well-known/jwks.json. */
@Configuration
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties, ResourceLoader resourceLoader) throws JOSEException {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (hasText(properties.privateKey()) && hasText(properties.publicKey())) {
            privateKey = RsaKeyConverters.pkcs8().convert(open(properties.privateKey(), resourceLoader));
            publicKey = RsaKeyConverters.x509().convert(open(properties.publicKey(), resourceLoader));
        } else if (properties.allowGeneratedKey()) {
            log.warn("No JWT key pair configured: generating a temporary one. Tokens will not survive a restart "
                    + "and will not be accepted by other instances. Configure app.jwt.private-key/public-key.");
            KeyPair keyPair = generateKeyPair();
            publicKey = (RSAPublicKey) keyPair.getPublic();
            privateKey = (RSAPrivateKey) keyPair.getPrivate();
        } else {
            throw new IllegalStateException("app.jwt.private-key and app.jwt.public-key must be configured");
        }
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        String keyId = hasText(properties.keyId()) ? properties.keyId() : key.computeThumbprint().toString();
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(RSAKey jwtSigningKey) {
        return new ImmutableJWKSet<>(new JWKSet(jwtSigningKey));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    private static InputStream open(String value, ResourceLoader resourceLoader) {
        String trimmed = value.trim();
        if (trimmed.startsWith("-----BEGIN")) {
            return new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8));
        }
        try {
            return resourceLoader.getResource(trimmed).getInputStream();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read JWT key from " + trimmed, ex);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
