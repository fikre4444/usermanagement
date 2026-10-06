package com.usermanagement.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.usermanagement.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class AdminIntegrationTest extends IntegrationTest {

    @Test
    void regularUsersCannotUseAdminEndpoints() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String token = accessToken(email, PASSWORD);

        MvcResult result = perform(get("/api/v1/admin/users"), null, token);

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(result).get("code").asString()).isEqualTo("ACCESS_DENIED");
        assertThat(perform(get("/api/v1/admin/roles"), null, token).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void adminsCreateUsersOfTypesThatCannotSelfRegister() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();

        MvcResult created = perform(post("/api/v1/admin/users"), Map.of(
                "userType", "dispatcher",
                "email", email,
                "firstName", "Dawit",
                "attributes", Map.of("region", "NORTH", "employeeNumber", 42),
                "roles", Set.of("SHIPPER")), admin);

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode user = body(created);
        assertThat(user.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(user.get("roles").toString()).contains("DISPATCHER", "SHIPPER");
        assertThat(user.get("attributes").get("employeeNumber").asInt()).isEqualTo(42);

        // Without a password, the new user sets one through the reset flow.
        perform(post("/api/v1/auth/password/forgot"), Map.of("identifier", email), null);
        perform(post("/api/v1/auth/password/reset"), Map.of(
                "identifier", email, "code", emailInbox.takeCode(email), "newPassword", PASSWORD), null);
        JsonNode tokens = login(email, PASSWORD);
        assertThat(tokens.get("user").get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    void adminsSearchUsersByTypeRoleTextAndAttributes() throws Exception {
        String admin = adminToken();
        String phone = uniquePhone();
        Map<String, Object> registration = driverRegistration(phone);
        registration.put("lastName", "Searchable" + phone.substring(5));
        String id = body(perform(post("/api/v1/auth/register"), registration, null)).get("id").asString();

        JsonNode page = body(perform(get("/api/v1/admin/users")
                .param("userType", "driver")
                .param("role", "DRIVER")
                .param("q", "searchable" + phone.substring(5))
                .param("attr", "vehicleType:VAN"), null, admin));
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("id").asString()).isEqualTo(id);

        JsonNode none = body(perform(get("/api/v1/admin/users")
                .param("q", "searchable" + phone.substring(5))
                .param("attr", "vehicleType:TRUCK"), null, admin));
        assertThat(none.get("totalElements").asInt()).isZero();

        MvcResult badSort = perform(get("/api/v1/admin/users").param("sort", "nope"), null, admin);
        assertThat(badSort.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void suspendingAUserBlocksSignInAndRevokesSessions() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String id = registerVerifiedShipper(email).get("id").asString();
        String refreshToken = login(email, PASSWORD).get("refreshToken").asString();

        JsonNode suspended = body(perform(put("/api/v1/admin/users/" + id + "/status"),
                Map.of("status", "SUSPENDED"), admin));
        assertThat(suspended.get("status").asString()).isEqualTo("SUSPENDED");

        MvcResult blocked = perform(post("/api/v1/auth/login"), Map.of("identifier", email, "password", PASSWORD), null);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asString()).isEqualTo("ACCOUNT_DISABLED");
        assertThat(perform(post("/api/v1/auth/token/refresh"), Map.of("refreshToken", refreshToken), null)
                .getResponse().getStatus()).isEqualTo(401);

        perform(put("/api/v1/admin/users/" + id + "/status"), Map.of("status", "ACTIVE"), admin);
        login(email, PASSWORD);
    }

    @Test
    void adminsManageRolesAndAssignThem() throws Exception {
        String admin = adminToken();
        String id = registerVerifiedShipper(uniqueEmail()).get("id").asString();
        String roleName = "auditor-" + Long.toString(System.nanoTime(), 36);

        MvcResult created = perform(post("/api/v1/admin/roles"),
                Map.of("name", roleName, "description", "Reads everything", "permissions", List.of("users:read")),
                admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String normalized = body(created).get("name").asString();
        assertThat(normalized).isEqualTo(roleName.toUpperCase().replace('-', '_'));

        MvcResult unknownRole = perform(put("/api/v1/admin/users/" + id + "/roles"),
                Map.of("roles", List.of("NOPE")), admin);
        assertThat(unknownRole.getResponse().getStatus()).isEqualTo(404);

        JsonNode assigned = body(perform(put("/api/v1/admin/users/" + id + "/roles"),
                Map.of("roles", List.of(normalized, "SHIPPER")), admin));
        assertThat(assigned.get("permissions").toString()).contains("users:read");

        assertThat(body(perform(delete("/api/v1/admin/roles/" + normalized), null, admin)).get("code").asString())
                .isEqualTo("ROLE_IN_USE");
        assertThat(body(perform(delete("/api/v1/admin/roles/ADMIN"), null, admin)).get("code").asString())
                .isEqualTo("SYSTEM_ROLE");

        perform(put("/api/v1/admin/users/" + id + "/roles"), Map.of("roles", List.of("SHIPPER")), admin);
        assertThat(perform(delete("/api/v1/admin/roles/" + normalized), null, admin).getResponse().getStatus())
                .isEqualTo(204);
    }

    @Test
    void userManagersCannotGrantRolesWithoutRolesWritePermission() throws Exception {
        String admin = adminToken();
        String roleName = "manager-" + Long.toString(System.nanoTime(), 36);
        String role = body(perform(post("/api/v1/admin/roles"),
                Map.of("name", roleName, "permissions", List.of("users:read", "users:write")), admin))
                .get("name").asString();
        String email = uniqueEmail();
        String id = registerVerifiedShipper(email).get("id").asString();
        perform(put("/api/v1/admin/users/" + id + "/roles"), Map.of("roles", List.of(role)), admin);
        String manager = accessToken(email, PASSWORD);

        assertThat(perform(get("/api/v1/admin/users/" + id), null, manager).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(put("/api/v1/admin/users/" + id + "/roles"), Map.of("roles", List.of("ADMIN")), manager)
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(perform(post("/api/v1/admin/users"), Map.of("userType", "dispatcher", "email", uniqueEmail(),
                "attributes", Map.of("region", "EAST"), "roles", Set.of("ADMIN")), manager)
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void adminsCanSetAdminOnlyAttributesAndVerificationFlags() throws Exception {
        String admin = adminToken();
        String phone = uniquePhone();
        String id = body(perform(post("/api/v1/auth/register"), driverRegistration(phone), null)).get("id").asString();

        JsonNode updated = body(perform(patch("/api/v1/admin/users/" + id), Map.of(
                "attributes", Map.of("backgroundCheckPassed", true, "licenseNumber", "FIXED-0001"),
                "phoneVerified", true), admin));

        assertThat(updated.get("attributes").get("backgroundCheckPassed").asBoolean()).isTrue();
        assertThat(updated.get("attributes").get("licenseNumber").asString()).isEqualTo("FIXED-0001");
        assertThat(updated.get("phoneVerified").asBoolean()).isTrue();
    }

    @Test
    void adminsUnlockAndDeleteUsers() throws Exception {
        String admin = adminToken();
        String email = uniqueEmail();
        String id = registerVerifiedShipper(email).get("id").asString();
        for (int i = 0; i < 5; i++) {
            perform(post("/api/v1/auth/login"), Map.of("identifier", email, "password", "Wrong-Passw0rd"), null);
        }
        assertThat(body(perform(get("/api/v1/admin/users/" + id), null, admin)).get("lockedUntil").isNull()).isFalse();

        perform(post("/api/v1/admin/users/" + id + "/unlock"), null, admin);
        login(email, PASSWORD);

        assertThat(perform(delete("/api/v1/admin/users/" + id), null, admin).getResponse().getStatus()).isEqualTo(204);
        assertThat(perform(get("/api/v1/admin/users/" + id), null, admin).getResponse().getStatus()).isEqualTo(404);
    }
}
