package com.usermanagement.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.usermanagement.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class AuditIntegrationTest extends IntegrationTest {

    @Test
    void successfulAndFailedSignInsAreAuditedWithoutSecrets() throws Exception {
        String email = uniqueEmail();
        String userId = registerVerifiedShipper(email).get("id").asString();

        perform(post("/api/v1/auth/login").header("User-Agent", "audit-test"),
                Map.of("identifier", email, "password", "Wrong-Passw0rd"), null);
        login(email, PASSWORD);

        JsonNode failure = awaitAudit(record -> action(record, "auth.login")
                && "FAILURE".equals(text(record, "outcome")) && email.equals(text(record.get("details"), "identifier")));
        assertThat(text(failure, "errorCode")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(text(failure.get("details"), "password")).isEqualTo("***");
        assertThat(text(failure.get("actor"), "type")).isEqualTo("ANONYMOUS");
        assertThat(text(failure.get("request"), "path")).isEqualTo("/api/v1/auth/login");
        assertThat(text(failure.get("request"), "userAgent")).isEqualTo("audit-test");
        assertThat(text(failure.get("request"), "requestId")).isNotBlank();
        assertThat(text(failure, "service")).isEqualTo("user-management-service");

        JsonNode success = awaitAudit(record -> action(record, "auth.login")
                && "SUCCESS".equals(text(record, "outcome")) && email.equals(text(record.get("details"), "identifier")));
        assertThat(text(success.get("target"), "type")).isEqualTo("user");
        assertThat(text(success.get("target"), "id")).isEqualTo(userId);
        assertThat(success.toString()).doesNotContain(PASSWORD);
    }

    @Test
    void adminActionsRecordTheActorTheTargetAndTheChange() throws Exception {
        String admin = adminToken();
        String userId = registerVerifiedShipper(uniqueEmail()).get("id").asString();

        perform(put("/api/v1/admin/users/" + userId + "/status"), Map.of("status", "SUSPENDED"), admin);

        JsonNode record = awaitAudit(r -> action(r, "admin.user.status-changed")
                && userId.equals(text(r.get("target"), "id")));
        assertThat(text(record, "outcome")).isEqualTo("SUCCESS");
        assertThat(text(record.get("actor"), "type")).isEqualTo("USER");
        assertThat(text(record.get("actor"), "userType")).isEqualTo("ADMIN");
        assertThat(record.get("actor").get("roles").toString()).contains("ADMIN");
        assertThat(text(record.get("details"), "status")).isEqualTo("SUSPENDED");
    }

    @Test
    void deniedAccessIsAudited() throws Exception {
        String email = uniqueEmail();
        String userId = registerVerifiedShipper(email).get("id").asString();
        String token = accessToken(email, PASSWORD);

        perform(get("/api/v1/admin/users"), null, token);
        perform(get("/api/v1/users/me"), null, null);

        JsonNode denied = awaitAudit(r -> action(r, "admin.user.searched")
                && userId.equals(text(r.get("actor"), "id")));
        assertThat(text(denied, "outcome")).isEqualTo("DENIED");
        assertThat(text(denied, "errorCode")).isEqualTo("ACCESS_DENIED");

        JsonNode unauthenticated = awaitAudit(r -> action(r, "security.unauthenticated")
                && "/api/v1/users/me".equals(text(r.get("request"), "path")));
        assertThat(text(unauthenticated, "outcome")).isEqualTo("DENIED");
    }

    @Test
    void requestsRejectedBeforeReachingTheEndpointAreAudited() throws Exception {
        String email = uniqueEmail();
        Map<String, Object> request = shipperRegistration(email);
        request.remove("password");

        perform(post("/api/v1/auth/register"), request, null);

        JsonNode record = awaitAudit(r -> action(r, "auth.register") && "VALIDATION_FAILED".equals(text(r, "errorCode"))
                && r.has("details") && r.get("details").has("errors")
                && r.get("details").get("errors").has("password"));
        assertThat(text(record, "outcome")).isEqualTo("FAILURE");
    }

    @Test
    void securityEventsAreAudited() throws Exception {
        String email = uniqueEmail();
        String userId = registerVerifiedShipper(email).get("id").asString();
        String refreshToken = login(email, PASSWORD).get("refreshToken").asString();

        perform(post("/api/v1/auth/token/refresh"), Map.of("refreshToken", refreshToken), null);
        perform(post("/api/v1/auth/token/refresh"), Map.of("refreshToken", refreshToken), null);
        for (int i = 0; i < 5; i++) {
            perform(post("/api/v1/auth/login"), Map.of("identifier", email, "password", "Wrong-Passw0rd"), null);
        }

        JsonNode reuse = awaitAudit(r -> action(r, "security.refresh-token-reuse")
                && userId.equals(text(r.get("target"), "id")));
        assertThat(text(reuse, "outcome")).isEqualTo("DENIED");
        JsonNode locked = awaitAudit(r -> action(r, "security.account-locked")
                && userId.equals(text(r.get("target"), "id")));
        assertThat(locked.get("details").has("lockedUntil")).isTrue();
    }

    @Test
    void codesAndNewPasswordsAreRedacted() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        perform(post("/api/v1/auth/password/forgot"), Map.of("identifier", email), null);
        String code = emailInbox.takeCode(email);

        perform(post("/api/v1/auth/password/reset"),
                Map.of("identifier", email, "code", code, "newPassword", "N3w-Password"), null);

        JsonNode record = awaitAudit(r -> action(r, "auth.password.reset")
                && email.equals(text(r.get("details"), "identifier")));
        assertThat(text(record.get("details"), "code")).isEqualTo("***");
        assertThat(text(record.get("details"), "newPassword")).isEqualTo("***");
        assertThat(record.toString()).doesNotContain(code).doesNotContain("N3w-Password");
    }

    @Test
    void systemActionsAreAuditedWithTheSystemActor() {
        JsonNode record = awaitAudit(r -> action(r, "system.roles-synchronized"));

        assertThat(text(record.get("actor"), "type")).isEqualTo("SYSTEM");
        assertThat(record.get("details").get("roles").toString()).contains("ADMIN", "DRIVER");
    }

    private static boolean action(JsonNode record, String action) {
        return action.equals(text(record, "action"));
    }

    private static String text(JsonNode node, String field) {
        return node == null || node.get(field) == null || node.get(field).isNull() ? null : node.get(field).asString();
    }
}
