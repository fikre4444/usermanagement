package com.usermanagement.events;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores every published {@link DomainEvent} in the outbox table, in the same transaction as the
 * change that produced it: either both are committed or neither is.
 */
@Component
class OutboxWriter {

    private final OutboxStore store;

    OutboxWriter(OutboxStore store) {
        this.store = store;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(DomainEvent event) {
        store.append(event.stream(), event.eventType(), event.aggregateType(), event.aggregateId(),
                event.occurredAt(), event);
    }
}
