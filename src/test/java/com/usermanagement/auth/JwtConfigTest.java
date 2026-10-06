package com.usermanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void loadsInlinePemKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAKey key = config.jwtSigningKey(properties(pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", keyPair.getPublic().getEncoded()), false), new DefaultResourceLoader());

        assertThat(key.toRSAPublicKey()).isEqualTo(keyPair.getPublic());
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.getKeyID()).isNotBlank();
    }

    @Test
    void refusesToStartWithoutKeysWhenGenerationIsDisabled() {
        assertThatThrownBy(() -> config.jwtSigningKey(properties(null, null, false), new DefaultResourceLoader()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.private-key");
    }

    private static JwtProperties properties(String privateKey, String publicKey, boolean allowGenerated) {
        return new JwtProperties("test", Duration.ofMinutes(5), Duration.ofDays(1), privateKey, publicKey, null,
                allowGenerated);
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }
}
