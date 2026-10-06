package com.usermanagement.user;

import com.usermanagement.common.error.ApiException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalises and validates the three ways a user can be identified. Emails and usernames are
 * case-insensitive (stored lower case); phone numbers are stored in E.164 format.
 */
public final class Identifiers {

    public enum Kind { EMAIL, PHONE, USERNAME }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,49}$");
    private static final Pattern PHONE_FORMATTING = Pattern.compile("[\\s().-]");

    private Identifiers() {
    }

    /** Returns the normalised email, {@code null} for blank input, or throws a validation error. */
    public static String email(String raw) {
        return normalize(raw, Kind.EMAIL)
                .map(value -> require(value, EMAIL, "email", "must be a valid email address"))
                .orElse(null);
    }

    public static String phone(String raw) {
        return normalize(raw, Kind.PHONE)
                .map(value -> require(value, PHONE, "phone",
                        "must be in international format, e.g. +251911234567"))
                .orElse(null);
    }

    public static String username(String raw) {
        return normalize(raw, Kind.USERNAME)
                .map(value -> require(value, USERNAME, "username",
                        "must be 3-50 characters: letters, digits, '.', '_' or '-'"))
                .orElse(null);
    }

    /** Guesses what kind of identifier a login string is. */
    public static Kind kindOf(String identifier) {
        String value = identifier == null ? "" : identifier.trim();
        if (value.contains("@")) {
            return Kind.EMAIL;
        }
        if (value.startsWith("+")) {
            return Kind.PHONE;
        }
        return Kind.USERNAME;
    }

    /** Normalises without validating; returns empty for blank input. */
    public static Optional<String> normalize(String raw, Kind kind) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();
        return Optional.of(switch (kind) {
            case EMAIL, USERNAME -> value.toLowerCase(Locale.ROOT);
            case PHONE -> PHONE_FORMATTING.matcher(value).replaceAll("");
        });
    }

    private static String require(String value, Pattern pattern, String field, String message) {
        if (!pattern.matcher(value).matches()) {
            throw ApiException.validation(field, message);
        }
        return value;
    }
}
