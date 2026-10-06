package com.usermanagement.user;

import java.util.Map;

/**
 * A partial profile update. {@code null} means "leave unchanged"; an empty string clears an optional
 * field. Attributes follow the same rule: omitted keys are kept, a {@code null} value removes one.
 * The verification flags are only honoured for administrators.
 */
public record ProfileUpdate(
        String firstName,
        String lastName,
        String username,
        String email,
        String phone,
        Map<String, Object> attributes,
        Boolean emailVerified,
        Boolean phoneVerified) {
}
