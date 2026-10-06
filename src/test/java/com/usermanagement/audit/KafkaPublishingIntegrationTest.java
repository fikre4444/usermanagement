package com.usermanagement.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.usermanagement.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;

/** End to end: activity in the service ends up on the Kafka topics, as an audit service would consume it. */
@TestPropertySource(properties = "app.events.kafka.enabled=true")
class KafkaPublishingIntegrationTest extends IntegrationTest {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    static {
        KAFKA.start();
    }

    @Test
    void auditRecordsAndDomainEventsArePublishedToTheirTopics() throws Exception {
        String email = uniqueEmail();
        String userId = registerVerifiedShipper(email).get("id").asString();
        login(email, PASSWORD);

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of("user-management.audit", "user-management.domain-events"));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                return received.stream().anyMatch(r -> r.topic().equals("user-management.audit")
                        && userId.equals(r.key()) && r.value().contains("\"auth.login\""))
                        && received.stream().anyMatch(r -> r.topic().equals("user-management.domain-events")
                        && userId.equals(r.key()) && r.value().contains("\"user.registered\""));
            });

            ConsumerRecord<String, String> loginRecord = received.stream()
                    .filter(r -> r.topic().equals("user-management.audit") && userId.equals(r.key())
                            && r.value().contains("\"auth.login\""))
                    .findFirst().orElseThrow();
            JsonNode envelope = json.readTree(loginRecord.value());
            assertThat(envelope.get("stream").asString()).isEqualTo("audit");
            assertThat(envelope.get("type").asString()).isEqualTo("auth.login");
            assertThat(envelope.get("id").asString()).isNotBlank();
            assertThat(envelope.get("data").get("outcome").asString()).isEqualTo("SUCCESS");
            assertThat(envelope.get("data").get("target").get("id").asString()).isEqualTo(userId);
            assertThat(header(loginRecord, "event-stream")).isEqualTo("audit");
            assertThat(header(loginRecord, "event-type")).isEqualTo("auth.login");
            assertThat(header(loginRecord, "event-id")).isEqualTo(envelope.get("id").asString());
        }
    }

    private static KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "audit-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
