package com.usermanagement.user;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable copy of a user's public state, used in events and extension points. */
public record UserSnapshot(
        UUID id,
        String userType,
        String username,
        String email,
        String phone,
        String firstName,
        String lastName,
        UserStatus status,
        boolean emailVerified,
        boolean phoneVerified,
        Set<String> roles,
        Map<String, Object> attributes) {

    public static UserSnapshot of(User user) {
        return new UserSnapshot(user.getId(), user.getUserType(), user.getUsername(), user.getEmail(),
                user.getPhone(), user.getFirstName(), user.getLastName(), user.getStatus(), user.isEmailVerified(),
                user.isPhoneVerified(), Set.copyOf(user.roleNames()), Map.copyOf(user.getAttributes()));
    }
}
