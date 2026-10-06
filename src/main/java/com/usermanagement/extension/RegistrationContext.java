package com.usermanagement.extension;

import com.usermanagement.usertype.Actor;
import com.usermanagement.usertype.UserType;
import java.util.Map;

/** The normalised registration data handed to {@link RegistrationValidator}s. */
public record RegistrationContext(
        UserType userType,
        String username,
        String email,
        String phone,
        String firstName,
        String lastName,
        Map<String, Object> attributes,
        Actor actor) {
}
