package com.usermanagement.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        /* Failed password attempts before the account is temporarily locked. */
        @DefaultValue("5") int maxFailedAttempts,
        @DefaultValue("15m") Duration lockDuration,
        /* Allow passwordless sign-in with a one-time code sent by email or SMS. */
        @DefaultValue("true") boolean otpLoginEnabled) {
}
