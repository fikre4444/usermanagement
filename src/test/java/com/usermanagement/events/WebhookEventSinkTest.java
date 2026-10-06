package com.usermanagement.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.usermanagement.common.crypto.SecureTokens;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WebhookEventSinkTest {

    private HttpServer server;
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private final AtomicInteger status = new AtomicInteger(204);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            received.put("signature", exchange.getRequestHeaders().getFirst("X-Signature"));
            received.put("type", exchange.getRequestHeaders().getFirst("X-Event-Type"));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsSignedEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        sink().publish(new OutboxMessage(id, Streams.DOMAIN_EVENTS, "user.deleted", "user", "abc", Instant.parse("2026-01-01T00:00:00Z"),
                "{\"userId\":\"abc\"}"));

        String body = received.get("body");
        assertThat(received.get("signature")).isEqualTo("sha256=" + SecureTokens.hmacSha256Hex("secret", body));
        assertThat(received.get("type")).isEqualTo("user.deleted");
        JsonNode envelope = JsonMapper.builder().build().readTree(body);
        assertThat(envelope.get("id").asString()).isEqualTo(id.toString());
        assertThat(envelope.get("stream").asString()).isEqualTo(Streams.DOMAIN_EVENTS);
        assertThat(envelope.get("data").get("userId").asString()).isEqualTo("abc");
    }

    @Test
    void handlesOnlyTheConfiguredStreams() {
        assertThat(sink().supports(Streams.DOMAIN_EVENTS)).isTrue();
        assertThat(sink().supports(Streams.AUDIT)).isFalse();
    }

    @Test
    void failsOnErrorResponsesSoTheEventIsRetried() {
        status.set(500);

        assertThatThrownBy(() -> sink().publish(new OutboxMessage(UUID.randomUUID(), Streams.DOMAIN_EVENTS, "user.deleted", "user", "abc",
                Instant.now(), "{}"))).isInstanceOf(Exception.class);
    }

    private WebhookEventSink sink() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/hook";
        return new WebhookEventSink(new EventsProperties.Webhook(url, "secret", Duration.ofSeconds(2), Set.of(Streams.DOMAIN_EVENTS)));
    }
}
