package com.usermanagement.user;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The first administrator, created at startup if no user with this email exists yet. */
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("admin") String userType,
        @DefaultValue("admin") String username,
        String email,
        String password,
        @DefaultValue("ADMIN") Set<String> roles) {
}
