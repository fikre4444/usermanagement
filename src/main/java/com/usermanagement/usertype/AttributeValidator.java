package com.usermanagement.usertype;

import com.usermanagement.common.error.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates and normalises the domain-specific {@code attributes} of a user against the schema of
 * its {@link UserType}. All problems are collected and reported together, keyed by
 * {@code attributes.<name>}.
 */
@Component
public class AttributeValidator {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /** Validates the attributes of a new user; required attributes must be present. */
    public Map<String, Object> forCreate(UserType type, Map<String, Object> input, Actor actor) {
        return apply(type, Map.of(), input, actor, true);
    }

    /**
     * Applies a partial update: attributes not mentioned are kept, a {@code null} value removes the
     * attribute. Only the attributes being changed are validated.
     */
    public Map<String, Object> forUpdate(UserType type, Map<String, Object> current, Map<String, Object> changes,
                                         Actor actor) {
        return apply(type, current, changes, actor, false);
    }

    private Map<String, Object> apply(UserType type, Map<String, Object> current, Map<String, Object> changes,
                                      Actor actor, boolean creating) {
        Map<String, Object> input = changes == null ? Map.of() : changes;
        Map<String, Object> result = new LinkedHashMap<>(current == null ? Map.of() : current);
        Map<String, String> errors = new LinkedHashMap<>();

        input.keySet().stream()
                .filter(name -> type.attribute(name).isEmpty())
                .forEach(name -> errors.put(field(name), "unknown attribute"));

        for (AttributeDefinition definition : type.attributes()) {
            String name = definition.name();
            boolean writable = definition.writableBy(actor, creating);
            if (!input.containsKey(name)) {
                if (creating && writable && definition.required()) {
                    errors.put(field(name), "is required");
                }
                continue;
            }
            if (!writable) {
                errors.put(field(name), "cannot be changed");
                continue;
            }
            Object raw = input.get(name);
            if (raw == null || (raw instanceof String text && text.isBlank())) {
                if (definition.required()) {
                    errors.put(field(name), "is required");
                } else {
                    result.remove(name);
                }
                continue;
            }
            try {
                result.put(name, convert(definition, raw));
            } catch (IllegalArgumentException ex) {
                errors.put(field(name), ex.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return result;
    }

    private Object convert(AttributeDefinition definition, Object raw) {
        return switch (definition.type()) {
            case STRING -> validateString(definition, requireString(raw));
            case EMAIL -> requireMatch(requireString(raw).trim().toLowerCase(), EMAIL, "must be a valid email address");
            case PHONE -> requireMatch(requireString(raw).replaceAll("[\\s().-]", ""), PHONE,
                    "must be a phone number in international format, e.g. +251911234567");
            case INTEGER -> toLong(checkRange(definition, toInteger(raw)));
            case NUMBER -> checkRange(definition, toDecimal(raw));
            case BOOLEAN -> toBoolean(raw);
            case DATE -> toDate(raw);
            case ENUM -> {
                String value = requireString(raw);
                if (!definition.allowedValues().contains(value)) {
                    throw new IllegalArgumentException("must be one of " + definition.allowedValues());
                }
                yield value;
            }
        };
    }

    private String validateString(AttributeDefinition definition, String value) {
        if (definition.minLength() != null && value.length() < definition.minLength()) {
            throw new IllegalArgumentException("must be at least " + definition.minLength() + " characters");
        }
        if (definition.maxLength() != null && value.length() > definition.maxLength()) {
            throw new IllegalArgumentException("must be at most " + definition.maxLength() + " characters");
        }
        if (definition.pattern() != null) {
            Pattern pattern = patternCache.computeIfAbsent(definition.pattern(), Pattern::compile);
            if (!pattern.matcher(value).matches()) {
                throw new IllegalArgumentException("has an invalid format");
            }
        }
        return value;
    }

    private static String requireString(Object raw) {
        if (raw instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("must be a string");
    }

    private static String requireMatch(String value, Pattern pattern, String message) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static BigDecimal toDecimal(Object raw) {
        try {
            if (raw instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            if (raw instanceof String text) {
                return new BigDecimal(text.trim());
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new IllegalArgumentException("must be a number");
    }

    private static BigDecimal toInteger(Object raw) {
        BigDecimal value;
        try {
            value = toDecimal(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("must be an integer");
        }
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("must be an integer");
        }
        return value;
    }

    private static Long toLong(BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("is out of range");
        }
    }

    private static BigDecimal checkRange(AttributeDefinition definition, BigDecimal value) {
        if (definition.min() != null && value.compareTo(definition.min()) < 0) {
            throw new IllegalArgumentException("must be greater than or equal to " + definition.min().toPlainString());
        }
        if (definition.max() != null && value.compareTo(definition.max()) > 0) {
            throw new IllegalArgumentException("must be less than or equal to " + definition.max().toPlainString());
        }
        return value;
    }

    private static Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if ("true".equalsIgnoreCase(String.valueOf(raw)) || "false".equalsIgnoreCase(String.valueOf(raw))) {
            return Boolean.valueOf(String.valueOf(raw));
        }
        throw new IllegalArgumentException("must be true or false");
    }

    private static String toDate(Object raw) {
        try {
            return LocalDate.parse(requireString(raw)).toString();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("must be a date in the format yyyy-MM-dd");
        }
    }

    private static String field(String name) {
        return "attributes." + name;
    }
}
