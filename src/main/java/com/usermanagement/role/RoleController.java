package com.usermanagement.role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/roles")
@Tag(name = "Admin: roles", description = "Manage roles and their permissions")
public class RoleController {

    static final String NAME_PATTERN = "^[A-Za-z][A-Za-z0-9_-]{1,49}$";
    static final String PERMISSION_PATTERN = "^[A-Za-z0-9_.:*-]{1,100}$";

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "List all roles")
    public List<RoleResponse> list() {
        return roleService.list().stream().map(RoleResponse::from).toList();
    }

    @GetMapping("/{name}")
    @PreAuthorize("hasAuthority('roles:read')")
    @Operation(summary = "Get a role")
    public RoleResponse get(@PathVariable String name) {
        return RoleResponse.from(roleService.get(name));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Create a role")
    public RoleResponse create(@Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(roleService.create(request.name(), request.description(), request.permissions()));
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Replace a role's description and permissions")
    public RoleResponse update(@PathVariable String name, @Valid @RequestBody UpdateRoleRequest request) {
        return RoleResponse.from(roleService.update(name, request.description(), request.permissions()));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('roles:write')")
    @Operation(summary = "Delete a role that is not declared in configuration and not assigned to anyone")
    public void delete(@PathVariable String name) {
        roleService.delete(name);
    }

    public record RoleResponse(String name, String description, boolean systemRole, Set<String> permissions) {

        static RoleResponse from(Role role) {
            return new RoleResponse(role.getName(), role.getDescription(), role.isSystemRole(),
                    new TreeSet<>(role.getPermissions()));
        }
    }

    public record CreateRoleRequest(
            @NotBlank @Pattern(regexp = NAME_PATTERN) String name,
            @Size(max = 255) String description,
            Set<@Pattern(regexp = PERMISSION_PATTERN) String> permissions) {
    }

    public record UpdateRoleRequest(
            @Size(max = 255) String description,
            Set<@Pattern(regexp = PERMISSION_PATTERN) String> permissions) {
    }
}
