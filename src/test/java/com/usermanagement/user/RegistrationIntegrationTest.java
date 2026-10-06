package com.usermanagement.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.usermanagement.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

class RegistrationIntegrationTest extends IntegrationTest {

    @Test
    void driverRegistersVerifiesPhoneAndSignsIn() throws Exception {
        String phone = uniquePhone();

        MvcResult registered = perform(post("/api/v1/auth/register"), driverRegistration(phone), null);
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        JsonNode user = body(registered);
        assertThat(user.get("userType").asString()).isEqualTo("DRIVER");
        assertThat(user.get("status").asString()).isEqualTo("PENDING_VERIFICATION");
        assertThat(user.get("roles").toString()).contains("DRIVER");
        assertThat(user.get("attributes").get("licenseNumber").asString()).isEqualTo("DL-12345");

        MvcResult notVerified = perform(post("/api/v1/auth/login"),
                Map.of("identifier", phone, "password", PASSWORD), null);
        assertThat(notVerified.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(notVerified).get("code").asString()).isEqualTo("ACCOUNT_NOT_VERIFIED");

        String code = smsInbox.takeCode(phone);
        MvcResult wrongCode = perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", phone, "code", code.equals("000000") ? "111111" : "000000"), null);
        assertThat(body(wrongCode).get("code").asString()).isEqualTo("INVALID_OTP");

        MvcResult confirmed = perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", phone, "code", code), null);
        assertThat(confirmed.getResponse().getStatus()).isEqualTo(200);

        JsonNode tokens = login(phone, PASSWORD);
        assertThat(tokens.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(tokens.get("user").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(tokens.get("user").get("phoneVerified").asBoolean()).isTrue();

        JsonNode claims = json.readTree(new String(
                Base64.getUrlDecoder().decode(tokens.get("accessToken").asString().split("\\.")[1]),
                StandardCharsets.UTF_8));
        assertThat(claims.get("sub").asString()).isEqualTo(user.get("id").asString());
        assertThat(claims.get("user_type").asString()).isEqualTo("DRIVER");
        assertThat(claims.get("roles").toString()).contains("DRIVER");
        assertThat(claims.get("permissions").toString()).contains("shipments:update-status");
        assertThat(claims.get("phone_number").asString()).isEqualTo(phone);
    }

    @Test
    void codesCannotBeReused() throws Exception {
        String email = uniqueEmail();
        perform(post("/api/v1/auth/register"), shipperRegistration(email), null);
        String code = emailInbox.takeCode(email);

        assertThat(perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", email, "code", code), null).getResponse().getStatus()).isEqualTo(200);
        assertThat(perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", email, "code", code), null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void verificationCodeCanBeRequestedAgain() throws Exception {
        String email = uniqueEmail();
        perform(post("/api/v1/auth/register"), shipperRegistration(email), null);
        String first = emailInbox.takeCode(email);

        MvcResult resent = perform(post("/api/v1/auth/verification/request"), Map.of("identifier", email), null);
        assertThat(resent.getResponse().getStatus()).isEqualTo(202);
        String second = emailInbox.takeCode(email);

        if (!first.equals(second)) {
            assertThat(perform(post("/api/v1/auth/verification/confirm"),
                    Map.of("identifier", email, "code", first), null).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(perform(post("/api/v1/auth/verification/confirm"),
                Map.of("identifier", email, "code", second), null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void attributesAreValidatedAgainstTheUserTypeSchema() throws Exception {
        Map<String, Object> request = driverRegistration(uniquePhone());
        request.put("attributes", Map.of("vehicleType", "BICYCLE", "backgroundCheckPassed", true, "color", "red"));

        MvcResult result = perform(post("/api/v1/auth/register"), request, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode problem = body(result);
        assertThat(problem.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(problem.get("errors").propertyNames()).contains(
                "attributes.licenseNumber", "attributes.vehicleType", "attributes.dateOfBirth",
                "attributes.backgroundCheckPassed", "attributes.color");
    }

    @Test
    void contactRequirementsOfTheUserTypeAreEnforced() throws Exception {
        Map<String, Object> request = driverRegistration(null);
        request.remove("phone");
        request.put("email", uniqueEmail());

        MvcResult result = perform(post("/api/v1/auth/register"), request, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("errors").get("phone").asString()).isEqualTo("is required");
    }

    @Test
    void onlySelfRegistrableEnabledTypesAreAccepted() throws Exception {
        Map<String, Object> dispatcher = shipperRegistration(uniqueEmail());
        dispatcher.put("userType", "dispatcher");
        MvcResult forbidden = perform(post("/api/v1/auth/register"), dispatcher, null);
        assertThat(forbidden.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(forbidden).get("code").asString()).isEqualTo("REGISTRATION_NOT_ALLOWED");

        Map<String, Object> disabled = shipperRegistration(uniqueEmail());
        disabled.put("userType", "user");
        MvcResult unknown = perform(post("/api/v1/auth/register"), disabled, null);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(unknown).get("code").asString()).isEqualTo("UNKNOWN_USER_TYPE");
    }

    @Test
    void duplicateIdentifiersAreRejected() throws Exception {
        String email = uniqueEmail();
        perform(post("/api/v1/auth/register"), shipperRegistration(email), null);

        MvcResult duplicate = perform(post("/api/v1/auth/register"), shipperRegistration(email.toUpperCase()), null);

        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate).get("code").asString()).isEqualTo("DUPLICATE_IDENTIFIER");
        assertThat(body(duplicate).get("errors").get("email").asString()).isEqualTo("is already in use");
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        Map<String, Object> request = shipperRegistration(uniqueEmail());
        request.put("password", "short");

        MvcResult result = perform(post("/api/v1/auth/register"), request, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).get("code").asString()).isEqualTo("PASSWORD_POLICY_VIOLATION");
    }

    @Test
    void userTypesAndTheirSchemasArePublic() throws Exception {
        JsonNode types = body(perform(get("/api/v1/user-types"), null, null));

        assertThat(types.findValuesAsString("code")).contains("DRIVER", "SHIPPER", "DISPATCHER", "ADMIN")
                .doesNotContain("USER");
        JsonNode driver = body(perform(get("/api/v1/user-types/driver"), null, null));
        assertThat(driver.get("phoneRequired").asBoolean()).isTrue();
        assertThat(driver.get("attributes").findValuesAsString("name")).contains("licenseNumber", "vehicleType");
        assertThat(perform(get("/api/v1/user-types/unknown"), null, null).getResponse().getStatus()).isEqualTo(404);
    }
}
