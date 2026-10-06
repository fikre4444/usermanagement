package com.usermanagement.usertype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.role.RoleProperties;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTypeRegistryTest {

    private final RoleProperties roles = new RoleProperties(Map.of("driver", new RoleProperties.Definition(null, null)));

    @Test
    void normalisesCodesAndResolvesTypes() {
        UserTypeRegistry registry = new UserTypeRegistry(new UserTypeProperties(Map.of(
                "fleet-driver", definition(true, Set.of("driver"), List.of()))), roles);

        assertThat(registry.requireEnabled("fleet-driver").code()).isEqualTo("FLEET_DRIVER");
        assertThat(registry.requireEnabled("FLEET_DRIVER").defaultRoles()).containsExactly("DRIVER");
    }

    @Test
    void disabledTypesCannotBeUsedForRegistration() {
        UserTypeRegistry registry = new UserTypeRegistry(new UserTypeProperties(Map.of(
                "old", definition(false, Set.of(), List.of()))), roles);

        assertThatThrownBy(() -> registry.requireEnabled("old")).isInstanceOf(ApiException.class);
        assertThat(registry.get("old").code()).isEqualTo("OLD");
        assertThat(registry.enabledTypes()).isEmpty();
    }

    @Test
    void invalidConfigurationFailsFast() {
        AttributeDefinition enumWithoutValues = new AttributeDefinition("kind", AttributeType.ENUM, false, null, null,
                null, null, null, null, null, null, null);
        AttributeDefinition badPattern = new AttributeDefinition("code", AttributeType.STRING, false, null, null,
                "[unclosed", null, null, null, null, null, null);

        assertThatThrownBy(() -> new UserTypeRegistry(new UserTypeProperties(Map.of(
                "broken", definition(true, Set.of("missing-role"), List.of(enumWithoutValues, badPattern)))), roles))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MISSING_ROLE")
                .hasMessageContaining("allowed-values")
                .hasMessageContaining("invalid pattern");
    }

    private static UserTypeProperties.Definition definition(boolean enabled, Set<String> roles,
                                                            List<AttributeDefinition> attributes) {
        return new UserTypeProperties.Definition(null, null, enabled, true, false, false, true, roles, attributes);
    }
}
