package com.usermanagement.events;

import java.time.Instant;

/**
 * An event other services may care about. Publishing one with Spring's
 * {@code ApplicationEventPublisher} inside a transaction stores it in the outbox, from where it is
 * reliably delivered to the configured {@link EventSink}s. The record's components form the payload.
 */
public interface DomainEvent {

    /** Stable, dotted name such as {@code user.registered}. */
    String eventType();

    String aggregateType();

    String aggregateId();

    Instant occurredAt();
}
