package com.usermanagement.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        /* Product name used in messages, e.g. "Acme Logistics". */
        @DefaultValue("User Management") String appName,
        /* Sender address for emails. */
        @DefaultValue("no-reply@example.com") String emailFrom,
        /* Whether the logging fallback senders print message bodies (which contain codes). Disable in production. */
        @DefaultValue("true") boolean logContent) {
}
