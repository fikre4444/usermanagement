package com.usermanagement.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.events")
public record EventsProperties(
        @DefaultValue Relay relay,
        @DefaultValue Webhook webhook,
        /* How long delivered events are kept before being purged. */
        @DefaultValue("7d") Duration retention) {

    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100") int batchSize,
            @DefaultValue("10") int maxAttempts,
            @DefaultValue("10s") Duration initialBackoff,
            @DefaultValue("1h") Duration maxBackoff) {
    }

    /** When {@code url} is set, every event is POSTed there as JSON, signed with {@code secret}. */
    public record Webhook(
            String url,
            String secret,
            @DefaultValue("5s") Duration timeout) {
    }
}
