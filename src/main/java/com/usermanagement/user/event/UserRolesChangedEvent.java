package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** The roles of a user were replaced. */
public record UserRolesChangedEvent(UUID userId, Set<String> roles, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.roles-changed";
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
