package com.usermanagement.user;

public enum UserStatus {
    /** Registered but has not yet proven ownership of an email address or phone number. */
    PENDING_VERIFICATION,
    ACTIVE,
    /** Disabled by an administrator; cannot sign in. */
    SUSPENDED,
    /** Deleted; personal data has been erased. */
    DELETED
}
