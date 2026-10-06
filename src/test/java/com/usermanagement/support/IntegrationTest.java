package com.usermanagement.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base class for end-to-end tests: the full application against a real PostgreSQL started with
 * Testcontainers (shared by all test classes), driven through HTTP with MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
public abstract class IntegrationTest {

    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String ADMIN_PASSWORD = "Admin-Passw0rd";
    public static final String PASSWORD = "Str0ng-Password";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    @Qualifier("emailInbox")
    protected CapturingNotificationSender emailInbox;

    @Autowired
    @Qualifier("smsInbox")
    protected CapturingNotificationSender smsInbox;

    @Autowired
    protected CapturingEventSink eventSink;

    // ------------------------------------------------------------------ data helpers

    protected static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
    }

    protected static String uniquePhone() {
        long digits = (long) (Math.random() * 1_000_000_000L);
        return "+2519" + String.format("%09d", digits).substring(1);
    }

    protected static Map<String, Object> driverAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("licenseNumber", "DL-12345");
        attributes.put("vehicleType", "VAN");
        attributes.put("vehicleCapacityKg", 1500);
        attributes.put("dateOfBirth", "1990-05-31");
        return attributes;
    }

    protected static Map<String, Object> driverRegistration(String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userType", "driver");
        body.put("phone", phone);
        body.put("password", PASSWORD);
        body.put("firstName", "Abebe");
        body.put("lastName", "Kebede");
        body.put("attributes", driverAttributes());
        return body;
    }

    protected static Map<String, Object> shipperRegistration(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userType", "shipper");
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("attributes", Map.of("companyName", "Blue Nile Trading", "businessType", "SME"));
        return body;
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected MvcResult perform(MockHttpServletRequestBuilder request, Object body, String accessToken)
            throws Exception {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return mvc.perform(request).andReturn();
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Registers a shipper and verifies the email; returns the user's JSON. */
    protected JsonNode registerVerifiedShipper(String email) throws Exception {
        JsonNode user = body(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(shipperRegistration(email))))
                .andExpect(status().isCreated()).andReturn());
        String code = emailInbox.takeCode(email);
        mvc.perform(post("/api/v1/auth/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", email, "code", code))))
                .andExpect(status().isOk());
        return user;
    }

    protected JsonNode login(String identifier, String password) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("identifier", identifier, "password", password))))
                .andExpect(status().isOk()).andReturn());
    }

    protected String accessToken(String identifier, String password) throws Exception {
        return login(identifier, password).get("accessToken").asString();
    }

    protected String adminToken() throws Exception {
        return accessToken(ADMIN_EMAIL, ADMIN_PASSWORD);
    }
}
