package com.usermanagement.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fallback sink used when no other sink is configured. */
public class LoggingEventSink implements EventSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventSink.class);

    @Override
    public void publish(OutboxMessage message) {
        log.info("Event {} {} for {} {}", message.id(), message.eventType(), message.aggregateType(),
                message.aggregateId());
    }
}
