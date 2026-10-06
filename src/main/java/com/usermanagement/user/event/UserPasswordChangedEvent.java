package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** A user's password was changed or reset. All sessions are revoked. */
public record UserPasswordChangedEvent(UUID userId, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.password-changed";
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
