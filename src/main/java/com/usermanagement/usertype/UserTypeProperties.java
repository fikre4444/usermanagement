package com.usermanagement.usertype;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * User types declared under {@code app.user-types.<code>}. The map key becomes the type code
 * ({@code fleet-manager} becomes {@code FLEET_MANAGER}).
 */
@ConfigurationProperties(prefix = "app")
public record UserTypeProperties(Map<String, Definition> userTypes) {

    public UserTypeProperties {
        userTypes = userTypes == null ? Map.of() : Map.copyOf(userTypes);
    }

    public record Definition(
            String displayName,
            String description,
            @DefaultValue("true") boolean enabled,
            boolean selfRegistration,
            boolean emailRequired,
            boolean phoneRequired,
            @DefaultValue("true") boolean verificationRequired,
            Set<String> defaultRoles,
            List<AttributeDefinition> attributes) {

        public Definition {
            defaultRoles = defaultRoles == null ? Set.of() : Set.copyOf(defaultRoles);
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }
    }
}
