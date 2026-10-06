package com.usermanagement.auth;

import com.usermanagement.audit.Audited;
import com.usermanagement.config.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/sessions")
@Tag(name = "My profile")
public class SessionController {

    private final RefreshTokenService refreshTokens;

    public SessionController(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Audited(action = "user.sessions.revoked", target = "user", targetId = "#actorId")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sign out everywhere",
            description = "Revokes all refresh tokens. Access tokens remain valid until they expire.")
    public void signOutEverywhere(@AuthenticationPrincipal Jwt jwt) {
        refreshTokens.revokeAll(CurrentUser.id(jwt));
    }
}
