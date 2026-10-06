package com.usermanagement.user.web;

import com.usermanagement.user.User;
import com.usermanagement.user.UserStatus;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
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
        Set<String> permissions,
        Map<String, Object> attributes,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUserType(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getFirstName(), user.getLastName(), user.getStatus(), user.isEmailVerified(),
                user.isPhoneVerified(), user.roleNames(), user.permissions(), user.getAttributes(),
                user.getLockedUntil(), user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
