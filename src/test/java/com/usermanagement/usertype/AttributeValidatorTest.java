package com.usermanagement.usertype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.usermanagement.common.error.ApiException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttributeValidatorTest {

    private final AttributeValidator validator = new AttributeValidator();

    private final UserType driver = new UserType("DRIVER", "Driver", null, true, true, false, true, true,
            Set.of("DRIVER"), List.of(
            attribute("licenseNumber", AttributeType.STRING, true, AttributeAccess.WRITE_ONCE, "^[A-Z0-9-]{5,20}$"),
            enumAttribute("vehicleType", List.of("VAN", "TRUCK")),
            new AttributeDefinition("capacity", AttributeType.NUMBER, false, null, null, null, null, null,
                    BigDecimal.ZERO, new BigDecimal("1000"), null, AttributeAccess.READ_WRITE),
            attribute("seats", AttributeType.INTEGER, false, AttributeAccess.READ_WRITE, null),
            attribute("dateOfBirth", AttributeType.DATE, false, AttributeAccess.READ_WRITE, null),
            attribute("approved", AttributeType.BOOLEAN, false, AttributeAccess.ADMIN_ONLY, null)));

    @Test
    void acceptsAndNormalisesValidAttributes() {
        Map<String, Object> result = validator.forCreate(driver, Map.of(
                "licenseNumber", "AB-12345",
                "vehicleType", "VAN",
                "capacity", "12.5",
                "seats", 4.0,
                "dateOfBirth", "1990-01-31"), Actor.USER);

        assertThat(result)
                .containsEntry("licenseNumber", "AB-12345")
                .containsEntry("capacity", new BigDecimal("12.5"))
                .containsEntry("seats", 4L)
                .containsEntry("dateOfBirth", "1990-01-31");
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertThatThrownBy(() -> validator.forCreate(driver, Map.of(
                "vehicleType", "BICYCLE",
                "capacity", 5000,
                "seats", 2.5,
                "dateOfBirth", "31/01/1990",
                "unknown", "x"), Actor.USER))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.errors())
                        .containsKeys("attributes.licenseNumber", "attributes.vehicleType", "attributes.capacity",
                                "attributes.seats", "attributes.dateOfBirth", "attributes.unknown"));
    }

    @Test
    void usersCannotWriteAdminOnlyAttributes() {
        Map<String, Object> input = new HashMap<>(Map.of("licenseNumber", "AB-12345", "vehicleType", "VAN"));
        input.put("approved", true);

        assertThatThrownBy(() -> validator.forCreate(driver, input, Actor.USER))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.errors())
                        .containsOnlyKeys("attributes.approved"));
        assertThat(validator.forCreate(driver, input, Actor.ADMIN)).containsEntry("approved", true);
    }

    @Test
    void writeOnceAttributesAreReadOnlyForUsersAfterRegistration() {
        Map<String, Object> current = Map.of("licenseNumber", "AB-12345", "vehicleType", "VAN");

        assertThatThrownBy(() -> validator.forUpdate(driver, current, Map.of("licenseNumber", "ZZ-99999"), Actor.USER))
                .isInstanceOf(ApiException.class);
        assertThat(validator.forUpdate(driver, current, Map.of("licenseNumber", "ZZ-99999"), Actor.ADMIN))
                .containsEntry("licenseNumber", "ZZ-99999");
    }

    @Test
    void partialUpdateKeepsOtherAttributesAndNullRemovesOptionalOnes() {
        Map<String, Object> current = Map.of("licenseNumber", "AB-12345", "vehicleType", "VAN", "seats", 2L);
        Map<String, Object> changes = new HashMap<>();
        changes.put("vehicleType", "TRUCK");
        changes.put("seats", null);

        Map<String, Object> result = validator.forUpdate(driver, current, changes, Actor.USER);

        assertThat(result).containsEntry("licenseNumber", "AB-12345").containsEntry("vehicleType", "TRUCK")
                .doesNotContainKey("seats");
    }

    @Test
    void requiredAttributesCannotBeRemoved() {
        Map<String, Object> changes = new HashMap<>();
        changes.put("vehicleType", null);

        assertThatThrownBy(() -> validator.forUpdate(driver, Map.of("vehicleType", "VAN"), changes, Actor.ADMIN))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.errors())
                        .containsEntry("attributes.vehicleType", "is required"));
    }

    private static AttributeDefinition attribute(String name, AttributeType type, boolean required,
                                                 AttributeAccess access, String pattern) {
        return new AttributeDefinition(name, type, required, null, null, pattern, null, null, null, null, null, access);
    }

    private static AttributeDefinition enumAttribute(String name, List<String> values) {
        return new AttributeDefinition(name, AttributeType.ENUM, true, null, null, null, null, null, null, null, values,
                AttributeAccess.READ_WRITE);
    }
}
