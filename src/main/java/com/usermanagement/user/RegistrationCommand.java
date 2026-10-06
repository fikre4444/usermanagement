package com.usermanagement.user;

import java.util.Map;
import java.util.Set;

/**
 * Everything needed to create a user.
 *
 * @param password   may be {@code null} for admin-created users, who then set it via "forgot password"
 * @param extraRoles roles in addition to the user type's default roles (admin only)
 */
public record RegistrationCommand(
        String userType,
        String username,
        String email,
        String phone,
        String password,
        String firstName,
        String lastName,
        Map<String, Object> attributes,
        Set<String> extraRoles) {

    public RegistrationCommand {
        attributes = attributes == null ? Map.of() : attributes;
        extraRoles = extraRoles == null ? Set.of() : extraRoles;
    }
}
