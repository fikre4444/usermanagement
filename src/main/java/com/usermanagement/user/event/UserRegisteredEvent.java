package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import com.usermanagement.user.UserSnapshot;
import java.time.Instant;

/** A user was registered (self-service or by an admin). */
public record UserRegisteredEvent(UserSnapshot user, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.registered";
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
