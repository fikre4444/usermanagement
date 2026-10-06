package com.usermanagement.common.error;

import java.util.Map;

/**
 * The single exception type used for expected, client-facing errors.
 * It is translated into an RFC 7807 problem response by {@link GlobalExceptionHandler}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, String> errors;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), Map.of());
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ErrorCode code, String message, Map<String, String> errors) {
        super(message);
        this.code = code;
        this.errors = errors == null ? Map.of() : Map.copyOf(errors);
    }

    public static ApiException validation(Map<String, String> errors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(), errors);
    }

    public static ApiException validation(String field, String message) {
        return validation(Map.of(field, message));
    }

    public ErrorCode code() {
        return code;
    }

    /** Field level errors, keyed by field path (e.g. {@code attributes.licenseNumber}). */
    public Map<String, String> errors() {
        return errors;
    }
}
