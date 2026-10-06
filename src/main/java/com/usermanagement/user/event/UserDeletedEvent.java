package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** A user was deleted and their personal data erased. */
public record UserDeletedEvent(UUID userId, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.deleted";
    }

    @Override
    public String aggregateType() {
        return "user";
    }

    @Override
    public String aggregateId() {
        return userId.toString();
    }
}
