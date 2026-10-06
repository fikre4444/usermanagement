package com.usermanagement.events;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Periodically delivers pending outbox messages to the sinks handling their stream (at-least-once). Failed
 * deliveries are retried with exponential backoff; after {@code max-attempts} the event is marked
 * as failed and left in the table for inspection.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final List<EventSink> customSinks;
    private final LoggingEventSink fallback;
    private final EventsProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    OutboxRelay(OutboxRepository repository, List<EventSink> available, LoggingEventSink fallback,
                EventsProperties properties, TransactionTemplate transactions, Clock clock) {
        this.repository = repository;
        this.customSinks = available.stream().filter(sink -> !(sink instanceof LoggingEventSink)).toList();
        this.fallback = fallback;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
        for (String stream : List.of(Streams.DOMAIN_EVENTS, Streams.AUDIT)) {
            log.info("Outbox stream '{}' is delivered to {}", stream,
                    sinksFor(stream).stream().map(sink -> sink.getClass().getSimpleName()).toList());
        }
    }

    @Scheduled(fixedDelayString = "${app.events.relay.interval:PT5S}",
            initialDelayString = "${app.events.relay.initial-delay:PT5S}")
    public void relay() {
        if (!properties.relay().enabled()) {
            return;
        }
        Integer processed;
        do {
            processed = transactions.execute(status -> publishNextBatch());
        } while (processed != null && processed == properties.relay().batchSize());
    }

    private int publishNextBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> batch = repository.lockNextBatch(now, properties.relay().batchSize());
        for (OutboxEvent event : batch) {
            try {
                OutboxMessage message = event.toMessage();
                for (EventSink sink : sinksFor(message.stream())) {
                    sink.publish(message);
                }
                event.markPublished(now);
            } catch (Exception ex) {
                EventsProperties.Relay relay = properties.relay();
                event.markAttemptFailed(now, ex.toString(), relay.maxAttempts(), relay.initialBackoff(),
                        relay.maxBackoff());
                log.warn("Delivery of {} message {} ({}) failed, attempt {}: {}", event.getStream(), event.getId(),
                        event.getEventType(), event.getAttempts(), ex.toString());
            }
        }
        return batch.size();
    }

    /** The custom sinks handling a stream, or the logging fallback if there are none. */
    private List<EventSink> sinksFor(String stream) {
        List<EventSink> matching = customSinks.stream().filter(sink -> sink.supports(stream)).toList();
        return matching.isEmpty() ? List.of(fallback) : matching;
    }

    @Scheduled(cron = "${app.events.cleanup-cron:0 23 3 * * *}")
    public void purgeDelivered() {
        transactions.executeWithoutResult(status ->
                repository.deletePublishedBefore(clock.instant().minus(properties.retention())));
    }
}
