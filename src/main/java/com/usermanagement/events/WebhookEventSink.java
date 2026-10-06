package com.usermanagement.events;

import com.usermanagement.common.crypto.SecureTokens;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * POSTs each event as JSON to a URL. The body is signed with HMAC-SHA256 over the raw body, sent in
 * {@code X-Signature: sha256=<hex>}, so receivers can verify authenticity.
 * <pre>
 * {"id": "...", "type": "user.registered", "aggregateType": "user", "aggregateId": "...",
 *  "occurredAt": "...", "data": { ...event payload... }}
 * </pre>
 */
public class WebhookEventSink implements EventSink {

    private final RestClient client;
    private final String url;
    private final String secret;

    public WebhookEventSink(EventsProperties.Webhook webhook) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(webhook.timeout()).build());
        requestFactory.setReadTimeout(webhook.timeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
        this.url = webhook.url();
        this.secret = webhook.secret();
    }

    @Override
    public void publish(OutboxMessage message) {
        String body = "{\"id\":\"" + message.id() + "\""
                + ",\"type\":\"" + message.eventType() + "\""
                + ",\"aggregateType\":\"" + message.aggregateType() + "\""
                + ",\"aggregateId\":\"" + message.aggregateId() + "\""
                + ",\"occurredAt\":\"" + message.occurredAt() + "\""
                + ",\"data\":" + message.payload() + "}";
        RestClient.RequestBodySpec request = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Event-Id", message.id().toString())
                .header("X-Event-Type", message.eventType());
        if (secret != null && !secret.isBlank()) {
            request.header("X-Signature", "sha256=" + SecureTokens.hmacSha256Hex(secret, body));
        }
        request.body(body.getBytes(StandardCharsets.UTF_8)).retrieve().toBodilessEntity();
    }
}
