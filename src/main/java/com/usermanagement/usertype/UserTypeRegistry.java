package com.usermanagement.usertype;

import com.usermanagement.common.Codes;
import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import com.usermanagement.role.RoleProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Holds the user types declared in configuration. The configuration is validated when the
 * application starts so that mistakes fail fast instead of at the first registration.
 */
@Component
public class UserTypeRegistry {

    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private final Map<String, UserType> types;

    public UserTypeRegistry(UserTypeProperties properties, RoleProperties roleProperties) {
        Map<String, UserType> built = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        properties.userTypes().forEach((key, definition) -> {
            UserType type = toUserType(Codes.normalize(key), definition);
            validate(type, roleProperties.roles().keySet(), problems);
            built.put(type.code(), type);
        });
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid user type configuration:\n - " + String.join("\n - ", problems));
        }
        this.types = Map.copyOf(built);
    }

    /** A type that accepts new users (used for registration). */
    public UserType requireEnabled(String code) {
        return find(code).filter(UserType::enabled)
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_USER_TYPE,
                        "Unknown or disabled user type '" + code + "'", Map.of("userType", "unknown or disabled")));
    }

    /** The type of an existing user, regardless of whether it still accepts registrations. */
    public UserType get(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "User type '" + code + "' is used by existing users but no longer configured"));
    }

    public Optional<UserType> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(types.get(Codes.normalize(code)));
    }

    public Collection<UserType> enabledTypes() {
        return types.values().stream().filter(UserType::enabled).toList();
    }

    private static UserType toUserType(String code, UserTypeProperties.Definition definition) {
        return new UserType(
                code,
                definition.displayName() == null ? code : definition.displayName(),
                definition.description(),
                definition.enabled(),
                definition.selfRegistration(),
                definition.emailRequired(),
                definition.phoneRequired(),
                definition.verificationRequired(),
                definition.defaultRoles().stream().map(Codes::normalize).collect(Collectors.toUnmodifiableSet()),
                definition.attributes());
    }

    private static void validate(UserType type, Set<String> declaredRoles, List<String> problems) {
        String prefix = "app.user-types." + type.code() + ": ";
        type.defaultRoles().stream()
                .filter(role -> !declaredRoles.contains(role))
                .forEach(role -> problems.add(prefix + "default role '" + role + "' is not declared under app.roles"));

        Set<String> names = new HashSet<>();
        for (AttributeDefinition attribute : type.attributes()) {
            String name = attribute.name();
            if (name == null || !ATTRIBUTE_NAME.matcher(name).matches()) {
                problems.add(prefix + "invalid attribute name '" + name + "'");
                continue;
            }
            if (!names.add(name)) {
                problems.add(prefix + "duplicate attribute '" + name + "'");
            }
            if (attribute.type() == AttributeType.ENUM && attribute.allowedValues().isEmpty()) {
                problems.add(prefix + "attribute '" + name + "' is an ENUM without allowed-values");
            }
            if (attribute.pattern() != null) {
                try {
                    Pattern.compile(attribute.pattern());
                } catch (PatternSyntaxException ex) {
                    problems.add(prefix + "attribute '" + name + "' has an invalid pattern");
                }
            }
            if (attribute.minLength() != null && attribute.maxLength() != null
                    && attribute.minLength() > attribute.maxLength()) {
                problems.add(prefix + "attribute '" + name + "' has min-length > max-length");
            }
            if (attribute.min() != null && attribute.max() != null && attribute.min().compareTo(attribute.max()) > 0) {
                problems.add(prefix + "attribute '" + name + "' has min > max");
            }
        }
    }
}
