package com.usermanagement.events;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.events")
public record EventsProperties(
        @DefaultValue Relay relay,
        @DefaultValue Webhook webhook,
        @DefaultValue Kafka kafka,
        /* How long delivered messages are kept in the outbox table before being purged. */
        @DefaultValue("7d") Duration retention) {

    public record Relay(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("100") int batchSize,
            @DefaultValue("10") int maxAttempts,
            @DefaultValue("10s") Duration initialBackoff,
            @DefaultValue("1h") Duration maxBackoff) {
    }

    /** When {@code url} is set, messages of the given streams are POSTed there as JSON, signed with {@code secret}. */
    public record Webhook(
            String url,
            String secret,
            @DefaultValue("5s") Duration timeout,
            @DefaultValue("domain-events") Set<String> streams) {
    }

    /**
     * Publishing to Kafka. Producer settings (bootstrap servers, security...) use the standard
     * {@code spring.kafka.*} properties.
     *
     * @param topics stream → topic; only the streams listed here are published to Kafka
     */
    public record Kafka(
            @DefaultValue("false") boolean enabled,
            Map<String, String> topics,
            @DefaultValue("10s") Duration sendTimeout,
            /* Create the topics at startup if they don't exist (convenient for development). */
            @DefaultValue("true") boolean createTopics,
            @DefaultValue("3") int partitions,
            @DefaultValue("1") short replicationFactor) {

        public Kafka {
            if (topics == null || topics.isEmpty()) {
                Map<String, String> defaults = new LinkedHashMap<>();
                defaults.put(Streams.DOMAIN_EVENTS, "user-management.domain-events");
                defaults.put(Streams.AUDIT, "user-management.audit");
                topics = defaults;
            }
            topics = Map.copyOf(topics);
        }
    }
}
