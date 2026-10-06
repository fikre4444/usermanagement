package com.usermanagement.usertype;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A kind of user in the consuming domain (driver, shipper, customer...) and its rules. */
public record UserType(
        String code,
        String displayName,
        String description,
        boolean enabled,
        boolean selfRegistration,
        boolean emailRequired,
        boolean phoneRequired,
        boolean verificationRequired,
        Set<String> defaultRoles,
        List<AttributeDefinition> attributes) {

    public Optional<AttributeDefinition> attribute(String name) {
        return attributes.stream().filter(attribute -> attribute.name().equals(name)).findFirst();
    }
}
