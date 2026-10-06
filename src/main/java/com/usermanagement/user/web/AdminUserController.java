package com.usermanagement.user.web;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.web.PageResponse;
import com.usermanagement.user.ProfileUpdate;
import com.usermanagement.user.RegistrationCommand;
import com.usermanagement.user.RegistrationService;
import com.usermanagement.user.UserSearchCriteria;
import com.usermanagement.user.UserService;
import com.usermanagement.user.UserStatus;
import com.usermanagement.usertype.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: users", description = "Manage any user (requires users:read / users:write)")
public class AdminUserController {

    private final UserService userService;
    private final RegistrationService registrationService;

    public AdminUserController(UserService userService, RegistrationService registrationService) {
        this.userService = userService;
        this.registrationService = registrationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Search users")
    public PageResponse<UserResponse> search(
            @Parameter(description = "Free text on username, email, phone and names") @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String role,
            @Parameter(description = "Attribute filters as name:value, e.g. attr=vehicleType:TRUCK")
            @RequestParam(name = "attr", required = false) List<String> attributeFilters,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UserSearchCriteria criteria = new UserSearchCriteria(q, status, userType, role, parse(attributeFilters));
        return PageResponse.from(userService.search(criteria, pageable), UserResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('users:read')")
    @Operation(summary = "Get a user")
    public UserResponse get(@PathVariable UUID id) {
        return UserResponse.from(userService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('users:write') and "
            + "(#request.roles() == null or #request.roles().isEmpty() or hasAuthority('roles:write'))")
    @Operation(summary = "Create a user of any type",
            description = "The account is active immediately. Without a password, the user sets one via "
                    + "'forgot password'. Granting extra roles also requires roles:write.")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        RegistrationCommand command = new RegistrationCommand(request.userType(), request.username(), request.email(),
                request.phone(), request.password(), request.firstName(), request.lastName(), request.attributes(),
                request.roles());
        return UserResponse.from(registrationService.register(command, Actor.ADMIN));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Update a user's profile, contact details, verification flags or attributes")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        ProfileUpdate update = new ProfileUpdate(request.firstName(), request.lastName(), request.username(),
                request.email(), request.phone(), request.attributes(), request.emailVerified(),
                request.phoneVerified());
        return UserResponse.from(userService.updateProfile(id, update, Actor.ADMIN));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Activate or suspend a user", description = "Suspending signs out all sessions.")
    public UserResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return UserResponse.from(userService.changeStatus(id, request.status()));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('users:write') and hasAuthority('roles:write')")
    @Operation(summary = "Replace a user's roles", description = "Requires users:write and roles:write, so that "
            + "user managers cannot grant themselves more privileges.")
    public UserResponse assignRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest request) {
        return UserResponse.from(userService.assignRoles(id, request.roles()));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Unlock an account locked after failed sign-in attempts")
    public UserResponse unlock(@PathVariable UUID id) {
        return UserResponse.from(userService.unlock(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('users:write')")
    @Operation(summary = "Delete a user", description = "Erases personal data and signs out all sessions.")
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }

    private static Map<String, String> parse(List<String> filters) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (filters != null) {
            for (String filter : filters) {
                int separator = filter.indexOf(':');
                if (separator <= 0) {
                    throw ApiException.validation("attr", "must be in the form name:value");
                }
                parsed.put(filter.substring(0, separator), filter.substring(separator + 1));
            }
        }
        return parsed;
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 50) String userType,
            @Size(max = 50) String username,
            @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 128) String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            Map<String, Object> attributes,
            Set<String> roles) {
    }

    public record UpdateUserRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 50) String username,
            @Size(max = 254) String email,
            @Size(max = 30) String phone,
            Map<String, Object> attributes,
            Boolean emailVerified,
            Boolean phoneVerified) {
    }

    public record StatusRequest(@NotNull UserStatus status) {
    }

    public record RolesRequest(@NotNull Set<@NotBlank String> roles) {
    }
}
