package com.usermanagement.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.usermanagement.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class EventsIntegrationTest extends IntegrationTest {

    @Test
    void userLifecycleEventsAreDeliveredThroughTheOutbox() throws Exception {
        String email = uniqueEmail();
        String id = registerVerifiedShipper(email).get("id").asString();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(eventSink.messagesFor(id)).extracting(OutboxMessage::eventType)
                        .contains("user.registered", "user.verified"));

        List<OutboxMessage> messages = eventSink.messagesFor(id);
        OutboxMessage registered = messages.stream()
                .filter(message -> message.eventType().equals("user.registered")).findFirst().orElseThrow();
        JsonNode payload = json.readTree(registered.payload());
        assertThat(registered.aggregateType()).isEqualTo("user");
        assertThat(payload.propertyNames()).containsExactlyInAnyOrder("user", "occurredAt");
        assertThat(payload.get("user").get("email").asString()).isEqualTo(email);
        assertThat(payload.get("user").get("attributes").get("companyName").asString()).isEqualTo("Blue Nile Trading");
        assertThat(payload.get("user").has("passwordHash")).isFalse();
    }
}
