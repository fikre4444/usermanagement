package com.usermanagement.events;

import com.usermanagement.common.crypto.SecureTokens;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * POSTs each message of the configured streams as JSON (see {@link OutboxMessage#toEnvelopeJson()})
 * to a URL. The body is signed with HMAC-SHA256 over the raw body, sent in
 * {@code X-Signature: sha256=<hex>}, so receivers can verify authenticity.
 */
public class WebhookEventSink implements EventSink {

    private final RestClient client;
    private final String url;
    private final String secret;
    private final Set<String> streams;

    public WebhookEventSink(EventsProperties.Webhook webhook) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(webhook.timeout()).build());
        requestFactory.setReadTimeout(webhook.timeout());
        this.client = RestClient.builder().requestFactory(requestFactory).build();
        this.url = webhook.url();
        this.secret = webhook.secret();
        this.streams = Set.copyOf(webhook.streams());
    }

    @Override
    public boolean supports(String stream) {
        return streams.contains(stream);
    }

    @Override
    public void publish(OutboxMessage message) {
        String body = message.toEnvelopeJson();
        RestClient.RequestBodySpec request = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Event-Id", message.id().toString())
                .header("X-Event-Stream", message.stream())
                .header("X-Event-Type", message.eventType());
        if (secret != null && !secret.isBlank()) {
            request.header("X-Signature", "sha256=" + SecureTokens.hmacSha256Hex(secret, body));
        }
        request.body(body.getBytes(StandardCharsets.UTF_8)).retrieve().toBodilessEntity();
    }
}
