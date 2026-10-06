package com.usermanagement.user.event;

import com.usermanagement.events.DomainEvent;
import com.usermanagement.notification.Channel;
import java.time.Instant;
import java.util.UUID;

/** A user proved ownership of an email address or phone number. */
public record UserVerifiedEvent(UUID userId, Channel channel, Instant occurredAt) implements DomainEvent {

    @Override
    public String eventType() {
        return "user.verified";
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
