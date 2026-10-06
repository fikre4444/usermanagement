package com.usermanagement.events;

/** Names of the outbox streams. Each stream maps to its own destination (Kafka topic, webhook...). */
public final class Streams {

    /** Business facts other services react to ({@code user.registered}, {@code user.deleted}...). */
    public static final String DOMAIN_EVENTS = "domain-events";

    /** One record per activity (every API call and security-relevant system action), for audit logging. */
    public static final String AUDIT = "audit";

    private Streams() {
    }
}
