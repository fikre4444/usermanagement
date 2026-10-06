package com.usermanagement.events;

import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Appends messages to the outbox. Must be called inside a transaction; the message is delivered
 * by {@link OutboxRelay} once that transaction commits.
 */
@Component
public class OutboxStore {

    private final OutboxRepository repository;
    private final JsonMapper jsonMapper;

    OutboxStore(OutboxRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    /** @param payload any object; it is serialised to JSON */
    public void append(String stream, String eventType, String aggregateType, String aggregateId, Instant occurredAt,
                       Object payload) {
        repository.save(new OutboxEvent(stream, eventType, aggregateType, aggregateId, occurredAt,
                jsonMapper.writeValueAsString(payload)));
    }
}
