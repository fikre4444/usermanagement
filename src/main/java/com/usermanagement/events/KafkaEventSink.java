package com.usermanagement.events;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes outbox messages to Kafka, one topic per stream.
 * <ul>
 *   <li>Key: the aggregate id (for audit records: the target, else the actor), so all messages about
 *       one user land in the same partition and stay ordered.</li>
 *   <li>Value: the JSON envelope ({@link OutboxMessage#toEnvelopeJson()}).</li>
 *   <li>Headers: {@code event-id}, {@code event-stream}, {@code event-type}.</li>
 * </ul>
 * The send is confirmed synchronously; a failure makes the relay retry the message later.
 */
public class KafkaEventSink implements EventSink {

    private final KafkaTemplate<String, String> kafka;
    private final Map<String, String> topics;
    private final Duration sendTimeout;

    public KafkaEventSink(KafkaTemplate<String, String> kafka, EventsProperties.Kafka properties) {
        this.kafka = kafka;
        this.topics = properties.topics();
        this.sendTimeout = properties.sendTimeout();
    }

    @Override
    public boolean supports(String stream) {
        return topics.containsKey(stream);
    }

    @Override
    public void publish(OutboxMessage message) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topics.get(message.stream()),
                message.aggregateId(), message.toEnvelopeJson());
        record.headers()
                .add("event-id", bytes(message.id().toString()))
                .add("event-stream", bytes(message.stream()))
                .add("event-type", bytes(message.eventType()));
        kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
