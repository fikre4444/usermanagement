package com.usermanagement.events;

/**
 * Extension point: where outbox events are delivered (webhook, Kafka, RabbitMQ, SNS...).
 * <p>
 * Register a Spring bean implementing this interface and it replaces the logging fallback.
 * Throw an exception to signal failure: the event will be retried with exponential backoff.
 */
public interface EventSink {

    void publish(OutboxMessage message) throws Exception;
}
