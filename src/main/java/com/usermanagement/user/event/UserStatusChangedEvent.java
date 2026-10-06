package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import com.usermanagement.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

/** An administrator activated or suspended a user. */
public record UserStatusChangedEvent(UUID userId, UserStatus previousStatus, UserStatus status, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.status-changed";
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
