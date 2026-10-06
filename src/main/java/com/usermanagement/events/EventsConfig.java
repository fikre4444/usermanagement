package com.usermanagement.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
class EventsConfig {

    @Bean
    LoggingEventSink loggingEventSink() {
        return new LoggingEventSink();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.events.webhook", name = "url")
    WebhookEventSink webhookEventSink(EventsProperties properties) {
        return new WebhookEventSink(properties.webhook());
    }

    /** Only active with {@code app.events.kafka.enabled=true}; nothing touches Kafka otherwise. */
    @Configuration
    @ConditionalOnProperty(prefix = "app.events.kafka", name = "enabled", havingValue = "true")
    static class KafkaSinkConfig {

        @Bean
        KafkaEventSink kafkaEventSink(KafkaTemplate<String, String> kafkaTemplate, EventsProperties properties) {
            return new KafkaEventSink(kafkaTemplate, properties.kafka());
        }

        @Bean
        @ConditionalOnProperty(prefix = "app.events.kafka", name = "create-topics", matchIfMissing = true)
        KafkaAdmin.NewTopics outboxTopics(EventsProperties properties) {
            EventsProperties.Kafka kafka = properties.kafka();
            return new KafkaAdmin.NewTopics(kafka.topics().values().stream()
                    .distinct()
                    .map(topic -> TopicBuilder.name(topic)
                            .partitions(kafka.partitions())
                            .replicas(kafka.replicationFactor())
                            .build())
                    .toArray(NewTopic[]::new));
        }
    }
}
