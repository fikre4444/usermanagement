package com.usermanagement.auth;

import com.usermanagement.user.UserStatus;
import com.usermanagement.user.event.UserDeletedEvent;
import com.usermanagement.user.event.UserPasswordChangedEvent;
import com.usermanagement.user.event.UserStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Revokes refresh tokens when the account's security context changes. Runs in the transaction that
 * produced the event, so revocation and change are atomic.
 */
@Component
class SessionRevocationListener {

    private final RefreshTokenService refreshTokens;

    SessionRevocationListener(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @EventListener
    void on(UserPasswordChangedEvent event) {
        refreshTokens.revokeAll(event.userId());
    }

    @EventListener
    void on(UserStatusChangedEvent event) {
        if (event.status() != UserStatus.ACTIVE) {
            refreshTokens.revokeAll(event.userId());
        }
    }

    @EventListener
    void on(UserDeletedEvent event) {
        refreshTokens.revokeAll(event.userId());
    }
}
