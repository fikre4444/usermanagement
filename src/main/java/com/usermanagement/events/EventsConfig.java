package com.usermanagement.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
