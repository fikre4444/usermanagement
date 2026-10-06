package com.usermanagement.user;

import com.usermanagement.common.Codes;
import com.usermanagement.role.Role;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the admin user search. Every field is optional.
 *
 * @param query      free text matched against username, email, phone, first and last name
 * @param attributes exact matches on domain attributes, e.g. {@code vehicleType -> TRUCK}
 */
public record UserSearchCriteria(String query, UserStatus status, String userType, String role,
                                 Map<String, String> attributes) {

    public UserSearchCriteria {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    Specification<User> toSpecification() {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), UserStatus.DELETED));
            }
            if (userType != null && !userType.isBlank()) {
                predicates.add(cb.equal(root.get("userType"), Codes.normalize(userType)));
            }
            if (role != null && !role.isBlank()) {
                Join<User, Role> roles = root.join("roles");
                predicates.add(cb.equal(roles.get("name"), Codes.normalize(role)));
                criteriaQuery.distinct(true);
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(root.get("phone"), pattern, '\\'),
                        cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("lastName")), pattern, '\\')));
            }
            attributes.forEach((name, value) -> predicates.add(cb.equal(
                    cb.function("jsonb_extract_path_text", String.class, root.get("attributes"), cb.literal(name)),
                    value)));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
