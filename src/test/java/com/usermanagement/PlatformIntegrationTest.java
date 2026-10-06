package com.usermanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.usermanagement.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class PlatformIntegrationTest extends IntegrationTest {

    @Test
    void jwksPublishesTheKeyUsedToSignTokens() throws Exception {
        String token = adminToken();
        JsonNode header = json.readTree(new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                StandardCharsets.UTF_8));

        JsonNode jwks = body(perform(get("/.well-known/jwks.json"), null, null));

        JsonNode key = jwks.get("keys").get(0);
        assertThat(header.get("alg").asString()).isEqualTo("RS256");
        assertThat(key.get("kid").asString()).isEqualTo(header.get("kid").asString());
        assertThat(key.get("kty").asString()).isEqualTo("RSA");
        assertThat(key.has("d")).as("private exponent must never be published").isFalse();
    }

    @Test
    void operationalEndpointsAreAvailable() throws Exception {
        assertThat(body(perform(get("/actuator/health"), null, null)).get("status").asString()).isEqualTo("UP");
        assertThat(perform(get("/actuator/health/readiness"), null, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(get("/actuator/prometheus"), null, null).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(get("/v3/api-docs"), null, null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void everyResponseCarriesARequestIdAndErrorsAreProblemDetails() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/user-types/unknown").header("X-Request-Id", "test-123"))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("test-123");
        assertThat(result.getResponse().getContentType()).contains("application/problem+json");
        JsonNode problem = body(result);
        assertThat(problem.get("status").asInt()).isEqualTo(404);
        assertThat(problem.get("code").asString()).isEqualTo("USER_TYPE_NOT_FOUND");
        assertThat(problem.get("requestId").asString()).isEqualTo("test-123");
    }
}
