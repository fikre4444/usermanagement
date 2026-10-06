package com.usermanagement.events;

import java.time.Instant;
import java.util.UUID;

/**
 * What an {@link EventSink} receives. {@code id} is unique per event and should be used by consumers
 * for de-duplication, since delivery is at-least-once.
 */
public record OutboxMessage(UUID id, String eventType, String aggregateType, String aggregateId,
                            Instant occurredAt, String payload) {
}
