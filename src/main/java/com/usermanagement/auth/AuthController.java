package com.usermanagement.auth;

import com.usermanagement.common.web.MessageResponse;
import com.usermanagement.user.RegistrationCommand;
import com.usermanagement.user.RegistrationService;
import com.usermanagement.user.VerificationService;
import com.usermanagement.user.web.UserResponse;
import com.usermanagement.usertype.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: registration, verification, sign-in, token refresh and password reset. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, verification, sign-in and password reset")
public class AuthController {

    private static final String ACCEPTED = "If an account matches, a code has been sent";

    private final RegistrationService registrationService;
    private final VerificationService verificationService;
    private final AuthService authService;
    private final Clock clock;

    public AuthController(RegistrationService registrationService, VerificationService verificationService,
                          AuthService authService, Clock clock) {
        this.registrationService = registrationService;
        this.verificationService = verificationService;
        this.authService = authService;
        this.clock = clock;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user",
            description = "Attributes are validated against the user type's schema (see GET /api/v1/user-types). "
                    + "If the type requires verification, a code is sent and the account stays "
                    + "PENDING_VERIFICATION until it is confirmed.")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        RegistrationCommand command = new RegistrationCommand(request.userType(), request.username(), request.email(),
                request.phone(), request.password(), request.firstName(), request.lastName(), request.attributes(),
                null);
        return UserResponse.from(registrationService.register(command, Actor.USER));
    }

    @PostMapping("/verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a verification code to an email address or phone number")
    public MessageResponse requestVerification(@Valid @RequestBody IdentifierRequest request) {
        verificationService.requestCode(request.identifier());
        return new MessageResponse(ACCEPTED);
    }

    @PostMapping("/verification/confirm")
    @Operation(summary = "Confirm an email address or phone number with the received code",
            description = "Activates a PENDING_VERIFICATION account.")
    public MessageResponse confirmVerification(@Valid @RequestBody CodeRequest request) {
        verificationService.confirm(request.identifier(), request.code());
        return new MessageResponse("Verified");
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with username, email or phone and a password")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(authService.login(request.identifier(), request.password()));
    }

    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a one-time sign-in code (passwordless sign-in)")
    public MessageResponse requestLoginCode(@Valid @RequestBody IdentifierRequest request) {
        authService.requestLoginCode(request.identifier());
        return new MessageResponse(ACCEPTED);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Sign in with a one-time code")
    public TokenResponse loginWithCode(@Valid @RequestBody CodeRequest request) {
        return toResponse(authService.loginWithCode(request.identifier(), request.code()));
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "Exchange a refresh token for new tokens",
            description = "Refresh tokens are single use: the response contains a new one.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out the session the refresh token belongs to")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Send a password reset code")
    public MessageResponse forgotPassword(@Valid @RequestBody IdentifierRequest request) {
        authService.forgotPassword(request.identifier());
        return new MessageResponse(ACCEPTED);
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Set a new password with a reset code", description = "Signs out all sessions.")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.identifier(), request.code(), request.newPassword());
    }

    private TokenResponse toResponse(AuthService.AuthTokens tokens) {
        return new TokenResponse(
                tokens.accessToken().value(),
                "Bearer",
                secondsUntil(tokens.accessToken().expiresAt()),
                tokens.refreshToken().value(),
                secondsUntil(tokens.refreshToken().expiresAt()),
                UserResponse.from(tokens.user()));
    }

    private long secondsUntil(Instant instant) {
        return Math.max(0, Duration.between(clock.instant(), instant).toSeconds());
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 50) String userType,
            @Size(max = 50) String username,
            @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            Map<String, Object> attributes) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(max = 128) String password) {
    }

    public record IdentifierRequest(@NotBlank @Size(max = 254) String identifier) {
    }

    public record CodeRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(max = 12) String code) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 254) String identifier,
            @NotBlank @Size(max = 12) String code,
            @NotBlank @Size(max = 128) String newPassword) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn,
            UserResponse user) {
    }
}
