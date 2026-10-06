package com.usermanagement.role;

import com.usermanagement.common.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    /** Declared in configuration; cannot be deleted through the API. */
    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 100)
    private Set<String> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String name, String description, boolean systemRole, Collection<String> permissions) {
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        this.permissions = new HashSet<>(permissions);
    }

    public void update(String description, Collection<String> permissions) {
        this.description = description;
        this.permissions.clear();
        this.permissions.addAll(permissions);
    }

    void markAsSystemRole() {
        this.systemRole = true;
    }

    void addPermissions(Collection<String> additional) {
        this.permissions.addAll(additional);
    }

    void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }
}
