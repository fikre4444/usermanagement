package com.usermanagement.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.usermanagement.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void refreshTokensRotateAndReuseRevokesTheSession() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String first = login(email, PASSWORD).get("refreshToken").asString();

        MvcResult rotated = perform(post("/api/v1/auth/token/refresh"), Map.of("refreshToken", first), null);
        assertThat(rotated.getResponse().getStatus()).isEqualTo(200);
        String second = body(rotated).get("refreshToken").asString();
        assertThat(second).isNotEqualTo(first);

        // Replaying the old token is treated as theft: it fails and kills the whole session.
        assertThat(refreshStatus(first)).isEqualTo(401);
        assertThat(refreshStatus(second)).isEqualTo(401);
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String refreshToken = login(email, PASSWORD).get("refreshToken").asString();

        assertThat(perform(post("/api/v1/auth/logout"), Map.of("refreshToken", refreshToken), null)
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(refreshStatus(refreshToken)).isEqualTo(401);
    }

    @Test
    void wrongCredentialsAreRejectedAndRepeatedFailuresLockTheAccount() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);

        for (int i = 0; i < 5; i++) {
            MvcResult failed = perform(post("/api/v1/auth/login"),
                    Map.of("identifier", email, "password", "Wrong-Passw0rd"), null);
            assertThat(failed.getResponse().getStatus()).isEqualTo(401);
            assertThat(body(failed).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
        }

        MvcResult locked = perform(post("/api/v1/auth/login"), Map.of("identifier", email, "password", PASSWORD), null);
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        assertThat(body(locked).get("code").asString()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void unknownUsersGetTheSameErrorAsWrongPasswords() throws Exception {
        MvcResult result = perform(post("/api/v1/auth/login"),
                Map.of("identifier", "nobody@example.com", "password", PASSWORD), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void passwordResetWithCodeUnlocksAndRevokesSessions() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);
        String oldRefreshToken = login(email, PASSWORD).get("refreshToken").asString();

        assertThat(perform(post("/api/v1/auth/password/forgot"), Map.of("identifier", email), null)
                .getResponse().getStatus()).isEqualTo(202);
        String code = emailInbox.takeCode(email);

        MvcResult weak = perform(post("/api/v1/auth/password/reset"),
                Map.of("identifier", email, "code", code, "newPassword", "weak"), null);
        assertThat(body(weak).get("code").asString()).isEqualTo("PASSWORD_POLICY_VIOLATION");

        MvcResult reset = perform(post("/api/v1/auth/password/reset"),
                Map.of("identifier", email, "code", code, "newPassword", "N3w-Password"), null);
        assertThat(reset.getResponse().getStatus()).isEqualTo(204);

        assertThat(perform(post("/api/v1/auth/login"), Map.of("identifier", email, "password", PASSWORD), null)
                .getResponse().getStatus()).isEqualTo(401);
        login(email, "N3w-Password");
        assertThat(refreshStatus(oldRefreshToken)).isEqualTo(401);
    }

    @Test
    void forgotPasswordDoesNotRevealWhetherAnAccountExists() throws Exception {
        MvcResult result = perform(post("/api/v1/auth/password/forgot"),
                Map.of("identifier", "nobody@example.com"), null);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void passwordlessSignInWithOneTimeCode() throws Exception {
        String email = uniqueEmail();
        registerVerifiedShipper(email);

        assertThat(perform(post("/api/v1/auth/otp/request"), Map.of("identifier", email), null)
                .getResponse().getStatus()).isEqualTo(202);
        String code = emailInbox.takeCode(email);

        MvcResult signedIn = perform(post("/api/v1/auth/otp/verify"),
                Map.of("identifier", email, "code", code), null);
        assertThat(signedIn.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(signedIn).get("accessToken").asString()).isNotBlank();
    }

    @Test
    void oneTimeCodeSignInAlsoVerifiesAPendingAccount() throws Exception {
        String phone = uniquePhone();
        perform(post("/api/v1/auth/register"), driverRegistration(phone), null);
        smsInbox.takeCode(phone); // registration code, unused

        perform(post("/api/v1/auth/otp/request"), Map.of("identifier", phone), null);
        JsonNode tokens = body(perform(post("/api/v1/auth/otp/verify"),
                Map.of("identifier", phone, "code", smsInbox.takeCode(phone)), null));

        assertThat(tokens.get("user").get("status").asString()).isEqualTo("ACTIVE");
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        MvcResult anonymous = perform(get("/api/v1/users/me"), null, null);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(anonymous).get("code").asString()).isEqualTo("UNAUTHORIZED");

        MvcResult forged = perform(get("/api/v1/users/me"), null, "not-a-real-token");
        assertThat(forged.getResponse().getStatus()).isEqualTo(401);
    }

    private int refreshStatus(String refreshToken) throws Exception {
        return perform(post("/api/v1/auth/token/refresh"), Map.of("refreshToken", refreshToken), null)
                .getResponse().getStatus();
    }
}
