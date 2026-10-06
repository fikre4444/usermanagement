package com.usermanagement.audit;

public enum AuditOutcome {
    /** The activity completed. */
    SUCCESS,
    /** The activity was attempted but failed (invalid input, wrong password, conflict...). */
    FAILURE,
    /** The activity was blocked by security (not authenticated, not authorised, token re-use...). */
    DENIED
}
