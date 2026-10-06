package com.usermanagement.user.web;

import com.usermanagement.audit.Audited;
import com.usermanagement.config.CurrentUser;
import com.usermanagement.user.ProfileUpdate;
import com.usermanagement.user.UserService;
import com.usermanagement.usertype.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Self-service profile management for the authenticated user. */
@RestController
@RequestMapping("/api/v1/users/me")
@Tag(name = "My profile", description = "The authenticated user's own account")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Audited(action = "user.profile.viewed", target = "user", targetId = "#actorId")
    @GetMapping
    @Operation(summary = "Get my profile")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(userService.get(CurrentUser.id(jwt)));
    }

    @Audited(action = "user.profile.updated", target = "user", targetId = "#actorId")
    @PatchMapping
    @Operation(summary = "Update my profile",
            description = "Omitted fields are unchanged; an empty string clears an optional field. "
                    + "Changing email or phone resets its verification and sends a new code.")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        ProfileUpdate update = new ProfileUpdate(request.firstName(), request.lastName(), request.username(),
                request.email(), request.phone(), request.attributes(), null, null);
        return UserResponse.from(userService.updateProfile(CurrentUser.id(jwt), update, Actor.USER));
    }

    @Audited(action = "user.password.changed", target = "user", targetId = "#actorId")
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password", description = "Signs out all sessions.")
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(CurrentUser.id(jwt), request.currentPassword(), request.newPassword());
    }

    @Audited(action = "user.account.deleted", target = "user", targetId = "#actorId")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my account", description = "Erases personal data and signs out all sessions.")
    public void delete(@AuthenticationPrincipal Jwt jwt) {
        userService.delete(CurrentUser.id(jwt));
    }

    public record UpdateProfileRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Size(max = 50) String username,
            @Size(max = 254) String email,
            @Size(max = 30) String phone,
            Map<String, Object> attributes) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {
    }
}
