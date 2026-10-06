package com.usermanagement.events;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores every published {@link DomainEvent} in the outbox table, in the same transaction as the
 * change that produced it: either both are committed or neither is.
 */
@Component
class OutboxWriter {

    private final OutboxRepository repository;
    private final JsonMapper jsonMapper;

    OutboxWriter(OutboxRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(DomainEvent event) {
        repository.save(new OutboxEvent(event, jsonMapper.writeValueAsString(event)));
    }
}
