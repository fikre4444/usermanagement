package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Read access to users by id or by any identifier (email, phone or username).
 * <p>
 * Deliberately not {@code @Transactional}: it throws {@link ApiException}s that callers running with
 * {@code noRollbackFor = ApiException.class} must be able to handle without their transaction being
 * marked rollback-only.
 */
@Component
public class UserLookup {

    private final UserRepository users;

    public UserLookup(UserRepository users) {
        this.users = users;
    }

    /** A user that has not been deleted, or {@code USER_NOT_FOUND}. */
    public User require(UUID id) {
        return findById(id).orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
    }

    /** A user that has not been deleted. */
    public Optional<User> findById(UUID id) {
        return users.findById(id).filter(user -> !user.isDeleted());
    }

    /** Finds a user by email, phone number or username. Never throws for malformed input. */
    public Optional<User> findByIdentifier(String identifier) {
        Identifiers.Kind kind = Identifiers.kindOf(identifier);
        return Identifiers.normalize(identifier, kind).flatMap(value -> switch (kind) {
            case EMAIL -> users.findByEmail(value);
            case PHONE -> users.findByPhone(value);
            case USERNAME -> users.findByUsername(value);
        });
    }
}
