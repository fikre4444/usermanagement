package com.usermanagement.audit;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(
        @DefaultValue("true") boolean enabled,
        /* Field names (case-insensitive, at any depth) whose values are replaced by "***" in details. */
        @DefaultValue({"password", "currentPassword", "newPassword", "code", "refreshToken", "accessToken", "token",
                "secret"}) Set<String> redactedFields) {
}
