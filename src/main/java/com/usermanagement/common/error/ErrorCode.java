package com.usermanagement.common.error;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return. The {@code code} is part of the public contract:
 * clients should branch on it rather than on the human readable message.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "The password does not satisfy the password policy"),
    INVALID_OTP(HttpStatus.BAD_REQUEST, "The code is invalid or has expired"),
    UNKNOWN_USER_TYPE(HttpStatus.BAD_REQUEST, "Unknown or disabled user type"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "The token is invalid or has expired"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required"),

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not allowed to perform this action"),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "The account has not been verified yet"),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "The account is disabled"),
    REGISTRATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Self-registration is not allowed for this user type"),
    FEATURE_DISABLED(HttpStatus.FORBIDDEN, "This feature is disabled"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Role not found"),
    USER_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "User type not found"),

    DUPLICATE_IDENTIFIER(HttpStatus.CONFLICT, "An account with this identifier already exists"),
    ROLE_ALREADY_EXISTS(HttpStatus.CONFLICT, "A role with this name already exists"),
    ROLE_IN_USE(HttpStatus.CONFLICT, "The role is still assigned to users"),
    SYSTEM_ROLE(HttpStatus.CONFLICT, "Roles declared in configuration cannot be deleted"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "The resource was modified concurrently, please retry"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "The request conflicts with existing data"),

    ACCOUNT_LOCKED(HttpStatus.LOCKED, "The account is temporarily locked after too many failed attempts"),

    OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, request a new code"),
    OTP_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "A code was sent recently, please wait before requesting another"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
