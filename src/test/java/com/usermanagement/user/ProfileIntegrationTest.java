package com.usermanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.usermanagement.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class ProfileIntegrationTest extends IntegrationTest {

    @Test
    void usersReadAndUpdateTheirProfile() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String token = accessToken(email, PASSWORD);

        JsonNode me = body(perform(get("/api/v1/users/me"), null, token));
        assertThat(me.get("email").asString()).isEqualTo(email);
        assertThat(me.get("attributes").get("companyName").asString()).isEqualTo("Blue Nile Trading");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("businessType", "ENTERPRISE");
        attributes.put("taxId", "0123456789");
        MvcResult updated = perform(patch("/api/v1/users/me"),
                Map.of("firstName", "Sara", "username", "sara.trading", "attributes", attributes), token);

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        JsonNode user = body(updated);
        assertThat(user.get("firstName").asString()).isEqualTo("Sara");
        assertThat(user.get("username").asString()).isEqualTo("sara.trading");
        assertThat(user.get("attributes").get("businessType").asString()).isEqualTo("ENTERPRISE");
        assertThat(user.get("attributes").get("companyName").asString()).isEqualTo("Blue Nile Trading");

        // The username can now be used to sign in.
        login("Sara.Trading", PASSWORD);
    }

    @Test
    void invalidAttributeUpdatesAreRejected() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String token = accessToken(email, PASSWORD);

        MvcResult result = perform(patch("/api/v1/users/me"),
                Map.of("attributes", Map.of("taxId", "abc", "nickname", "x")), token);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("errors").propertyNames()).contains("attributes.taxId", "attributes.nickname");
    }

    @Test
    void writeOnceAttributesCannotBeChangedByTheUser() throws Exception {
        String phone = uniquePhone();
        perform(post("/api/v1/auth/register"), driverRegistration(phone), null);
        perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", phone, "code", smsInbox.takeCode(phone)), null);
        String token = accessToken(phone, PASSWORD);

        MvcResult result = perform(patch("/api/v1/users/me"),
                Map.of("attributes", Map.of("licenseNumber", "NEW-99999")), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("errors").get("attributes.licenseNumber").asString())
                .isEqualTo("cannot be changed");

        MvcResult allowed = perform(patch("/api/v1/users/me"),
                Map.of("attributes", Map.of("vehicleType", "TRUCK")), token);
        assertThat(body(allowed).get("attributes").get("vehicleType").asString()).isEqualTo("TRUCK");
    }

    @Test
    void changingEmailRequiresVerifyingTheNewAddress() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String token = accessToken(email, PASSWORD);
        String newEmail = uniqueEmail();

        JsonNode user = body(perform(patch("/api/v1/users/me"), Map.of("email", newEmail), token));
        assertThat(user.get("email").asString()).isEqualTo(newEmail);
        assertThat(user.get("emailVerified").asBoolean()).isFalse();

        perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", newEmail, "code", emailInbox.takeCode(newEmail)), null);
        assertThat(body(perform(get("/api/v1/users/me"), null, token)).get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    void changingPasswordRequiresTheCurrentOneAndSignsOutEverywhere() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        JsonNode tokens = login(email, PASSWORD);
        String token = tokens.get("accessToken").asString();

        MvcResult wrong = perform(post("/api/v1/users/me/password"),
                Map.of("currentPassword", "Wrong-Passw0rd", "newPassword", "N3w-Password"), token);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(400);

        MvcResult changed = perform(post("/api/v1/users/me/password"),
                Map.of("currentPassword", PASSWORD, "newPassword", "N3w-Password"), token);
        assertThat(changed.getResponse().getStatus()).isEqualTo(204);

        assertThat(perform(post("/api/v1/auth/token/refresh"),
                Map.of("refreshToken", tokens.get("refreshToken").asString()), null).getResponse().getStatus())
                .isEqualTo(401);
        login(email, "N3w-Password");
    }

    @Test
    void signOutEverywhereRevokesAllRefreshTokens() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        JsonNode first = login(email, PASSWORD);
        JsonNode second = login(email, PASSWORD);

        assertThat(perform(delete("/api/v1/users/me/sessions"), null, first.get("accessToken").asString())
                .getResponse().getStatus()).isEqualTo(204);

        for (JsonNode session : new JsonNode[] {first, second}) {
            assertThat(perform(post("/api/v1/auth/token/refresh"),
                    Map.of("refreshToken", session.get("refreshToken").asString()), null).getResponse().getStatus())
                    .isEqualTo(401);
        }
    }

    @Test
    void deletingTheAccountErasesItAndFreesTheEmail() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String token = accessToken(email, PASSWORD);

        assertThat(perform(delete("/api/v1/users/me"), null, token).getResponse().getStatus()).isEqualTo(204);

        assertThat(perform(post("/api/v1/auth/login"), Map.of("identifier", email, "password", PASSWORD), null)
                .getResponse().getStatus()).isEqualTo(401);
        assertThat(perform(get("/api/v1/users/me"), null, token).getResponse().getStatus()).isEqualTo(404);
        assertThat(perform(post("/api/v1/auth/register"), shipperRegistration(email), null)
                .getResponse().getStatus()).isEqualTo(201);
    }
}
