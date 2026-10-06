package com.usermanagement.role;

import com.usermanagement.common.Codes;
import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roles;

    public RoleService(RoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public List<Role> list() {
        return roles.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Role get(String name) {
        return roles.findByName(Codes.normalize(name))
                .orElseThrow(() -> new ApiException(ErrorCode.ROLE_NOT_FOUND, "Role '" + name + "' not found"));
    }

    public Role create(String name, String description, Collection<String> permissions) {
        String normalized = Codes.normalize(name);
        if (roles.existsByName(normalized)) {
            throw new ApiException(ErrorCode.ROLE_ALREADY_EXISTS, "Role '" + normalized + "' already exists");
        }
        return roles.save(new Role(normalized, description, false, nullSafe(permissions)));
    }

    public Role update(String name, String description, Collection<String> permissions) {
        Role role = get(name);
        role.update(description, nullSafe(permissions));
        return role;
    }

    public void delete(String name) {
        Role role = get(name);
        if (role.isSystemRole()) {
            throw new ApiException(ErrorCode.SYSTEM_ROLE);
        }
        if (roles.countAssignments(role.getId()) > 0) {
            throw new ApiException(ErrorCode.ROLE_IN_USE);
        }
        roles.delete(role);
    }

    /** Loads roles by name, failing if any of them does not exist. */
    @Transactional(readOnly = true)
    public Set<Role> resolve(Collection<String> names) {
        Set<String> wanted = names.stream().map(Codes::normalize).collect(Collectors.toCollection(TreeSet::new));
        Set<Role> found = new HashSet<>(roles.findByNameIn(wanted));
        if (found.size() != wanted.size()) {
            Set<String> missing = new TreeSet<>(wanted);
            found.forEach(role -> missing.remove(role.getName()));
            throw new ApiException(ErrorCode.ROLE_NOT_FOUND, "Unknown role(s): " + String.join(", ", missing),
                    Map.of("roles", "unknown role(s): " + String.join(", ", missing)));
        }
        return found;
    }

    /**
     * Creates or updates one role declared in configuration. Permissions from configuration are added;
     * permissions granted at runtime through the API are kept.
     */
    public void syncConfiguredRole(String name, RoleProperties.Definition definition) {
        roles.findByName(name).ifPresentOrElse(role -> {
            role.markAsSystemRole();
            role.addPermissions(definition.permissions());
            if (definition.description() != null) {
                role.setDescription(definition.description());
            }
        }, () -> roles.save(new Role(name, definition.description(), true, definition.permissions())));
    }

    private static Set<String> nullSafe(Collection<String> permissions) {
        return permissions == null ? Set.of() : new HashSet<>(permissions);
    }
}
