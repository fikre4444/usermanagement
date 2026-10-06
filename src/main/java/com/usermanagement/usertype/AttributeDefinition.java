package com.usermanagement.usertype;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Schema of one domain-specific attribute of a user type, declared in configuration, e.g.
 * <pre>
 * - name: licenseNumber
 *   type: STRING
 *   required: true
 *   pattern: "^[A-Z0-9-]{5,20}$"
 *   access: WRITE_ONCE
 * </pre>
 */
public record AttributeDefinition(
        String name,
        @DefaultValue("STRING") AttributeType type,
        boolean required,
        String label,
        String description,
        String pattern,
        Integer minLength,
        Integer maxLength,
        BigDecimal min,
        BigDecimal max,
        List<String> allowedValues,
        @DefaultValue("READ_WRITE") AttributeAccess access) {

    public AttributeDefinition {
        type = type == null ? AttributeType.STRING : type;
        access = access == null ? AttributeAccess.READ_WRITE : access;
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
        label = label == null ? name : label;
    }

    public boolean writableBy(Actor actor, boolean creating) {
        if (actor == Actor.ADMIN) {
            return true;
        }
        return switch (access) {
            case READ_WRITE -> true;
            case WRITE_ONCE -> creating;
            case ADMIN_ONLY -> false;
        };
    }
}
