package com.usermanagement.usertype;

import com.usermanagement.common.error.ApiException;
import com.usermanagement.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public description of the configured user types, so client applications can render
 * registration and profile forms dynamically.
 */
@RestController
@RequestMapping("/api/v1/user-types")
@Tag(name = "User types", description = "Discover the kinds of users and their attribute schemas")
public class UserTypeController {

    private final UserTypeRegistry registry;

    public UserTypeController(UserTypeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(summary = "List the enabled user types and their attribute schemas")
    public List<UserTypeResponse> list() {
        return registry.enabledTypes().stream().map(UserTypeResponse::from).toList();
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get one user type and its attribute schema")
    public UserTypeResponse get(@PathVariable String code) {
        return registry.find(code)
                .filter(UserType::enabled)
                .map(UserTypeResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_TYPE_NOT_FOUND));
    }

    public record UserTypeResponse(
            String code,
            String displayName,
            String description,
            boolean selfRegistration,
            boolean emailRequired,
            boolean phoneRequired,
            boolean verificationRequired,
            List<AttributeDefinition> attributes) {

        static UserTypeResponse from(UserType type) {
            return new UserTypeResponse(type.code(), type.displayName(), type.description(), type.selfRegistration(),
                    type.emailRequired(), type.phoneRequired(), type.verificationRequired(), type.attributes());
        }
    }
}
