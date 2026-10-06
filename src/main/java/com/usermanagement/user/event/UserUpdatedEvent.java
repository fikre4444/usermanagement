package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import com.usermanagement.user.UserSnapshot;
import java.time.Instant;

/** A user's profile, contact details or attributes changed. */
public record UserUpdatedEvent(UserSnapshot user, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.updated";
    }

    @Override
    public String aggregateType() {
        return "user";
    }

    @Override
    public String aggregateId() {
        return user.id().toString();
    }
}
