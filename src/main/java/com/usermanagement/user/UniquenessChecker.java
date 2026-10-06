package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Checks that identifiers are not used by another account. The database unique constraints remain
 * the final guard against races; this check exists to return a precise error.
 */
@Component
class UniquenessChecker {

    private final UserRepository users;

    UniquenessChecker(UserRepository users) {
        this.users = users;
    }

    /** @param ownerId the user allowed to already own the identifiers ({@code null} for a new user) */
    void check(UUID ownerId, String username, String email, String phone) {
        Map<String, String> conflicts = new LinkedHashMap<>();
        checkOne("username", username, users::findByUsername, ownerId, conflicts);
        checkOne("email", email, users::findByEmail, ownerId, conflicts);
        checkOne("phone", phone, users::findByPhone, ownerId, conflicts);
        if (!conflicts.isEmpty()) {
            throw new ApiException(ErrorCode.DUPLICATE_IDENTIFIER, ErrorCode.DUPLICATE_IDENTIFIER.defaultMessage(),
                    conflicts);
        }
    }

    private static void checkOne(String field, String value, Function<String, Optional<User>> finder, UUID ownerId,
                                 Map<String, String> conflicts) {
        if (value != null && finder.apply(value).filter(user -> !user.getId().equals(ownerId)).isPresent()) {
            conflicts.put(field, "is already in use");
        }
    }
}
