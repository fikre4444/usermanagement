package com.usermanagement.role;

import com.usermanagement.common.Codes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Roles declared in configuration ({@code app.roles.<name>}). They are created or updated at
 * startup and flagged as system roles so they cannot be deleted through the API.
 */
@ConfigurationProperties(prefix = "app")
public record RoleProperties(Map<String, Definition> roles) {

    public RoleProperties {
        Map<String, Definition> normalized = new LinkedHashMap<>();
        if (roles != null) {
            roles.forEach((name, definition) -> normalized.put(Codes.normalize(name),
                    definition == null ? new Definition(null, null) : definition));
        }
        roles = Map.copyOf(normalized);
    }

    public record Definition(String description, Set<String> permissions) {

        public Definition {
            permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        }
    }
}
