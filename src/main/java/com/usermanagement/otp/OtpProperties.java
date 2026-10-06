package com.usermanagement.otp;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        @DefaultValue("6") int length,
        @DefaultValue("10m") Duration ttl,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("60s") Duration resendCooldown) {
}
