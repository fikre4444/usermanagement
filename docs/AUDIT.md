# Activity (audit) log

The service produces a log entry for **every activity** and publishes it so that a separate audit-log
microservice can store, search and report on it. This page is the contract for that consumer.

## What is recorded

| Source | Examples | How |
|---|---|---|
| Every API call | `auth.login`, `auth.register`, `user.profile.updated`, `admin.user.status-changed`, `admin.role.deleted`, and reads such as `admin.user.searched` and `user.profile.viewed` | `@Audited` on each endpoint. The entry is recorded on success, on failure (wrong password, conflict...) and on denial (missing permission) |
| Requests rejected before the endpoint runs | invalid body, malformed JSON | the same action, with outcome `FAILURE` and the field errors |
| Security events | `security.unauthenticated` (call without a valid token), `security.account-locked`, `security.refresh-token-reuse` | explicit calls to `AuditLog` |
| System actions | `system.roles-synchronized`, `system.admin-bootstrapped` | explicit calls, actor `SYSTEM` |

Public, read-only metadata (`/user-types`, `/.well-known/jwks.json`, actuator, API docs) is not audited.

### Actions

| Action | Target |
|---|---|
| `auth.register`, `auth.login`, `auth.otp-login`, `auth.token.refreshed` | user (on success) |
| `auth.verification.requested`, `auth.verification.confirmed`, `auth.otp-login.requested`, `auth.logout`, `auth.password.reset-requested`, `auth.password.reset` | (identifier in details) |
| `user.profile.viewed`, `user.profile.updated`, `user.password.changed`, `user.sessions.revoked`, `user.account.deleted` | the user themself |
| `admin.user.searched`, `admin.user.viewed`, `admin.user.created`, `admin.user.updated`, `admin.user.status-changed`, `admin.user.roles-changed`, `admin.user.unlocked`, `admin.user.deleted` | user |
| `admin.role.listed`, `admin.role.viewed`, `admin.role.created`, `admin.role.updated`, `admin.role.deleted` | role |
| `security.unauthenticated`, `security.account-locked`, `security.refresh-token-reuse` | user (except unauthenticated) |
| `system.roles-synchronized`, `system.admin-bootstrapped` | |

## Delivery

```
request ──▶ @Audited aspect ──▶ AuditLog ──▶ outbox_events (stream "audit", own transaction)
                                                    │
                          OutboxRelay (every 5 s) ──┴──▶ sinks for stream "audit":
                                                          Kafka topic user-management.audit (default)
                                                          or webhook / custom sink / log fallback
```

* **Decoupled.** Application code only calls `AuditLog`, or more often just uses the `@Audited` annotation.
  It doesn't know about Kafka. The transport is an `EventSink`, chosen by configuration. To switch to
  RabbitMQ or anything else, add one class (see [EXTENDING.md](EXTENDING.md#publish-to-another-broker-eg-rabbitmq)).
* **Reliable.** Entries are first written to PostgreSQL (transactional outbox), then published. If Kafka is down,
  entries wait and are retried with exponential backoff. Nothing is lost.
* **Independent of the activity's outcome.** Each entry is committed in its own transaction, so failed attempts are
  kept even though the activity's own changes are rolled back.
* **At-least-once.** De-duplicate on the envelope `id`.
* **Ordered per subject.** The Kafka key is the target id (else the actor id), so the history of one user is in a
  single partition and in order.
* **Fail-open.** If an entry can't even be written to the outbox (database down), the error is logged and the
  request carries on.

## Kafka message format

Topic `user-management.audit` (configurable). The key is the target id, else the actor id, else `anonymous` or `system`.

Headers: `event-id`, `event-stream` (`audit`), `event-type` (the action).

Value: a JSON envelope (the same one used for domain events and webhooks):

```json
{
  "id": "0b8f6c1e-4a51-4f0e-9a8e-3f1f7d2c9e11",
  "stream": "audit",
  "type": "admin.user.status-changed",
  "aggregateType": "user",
  "aggregateId": "9f0e2a3b-…",
  "occurredAt": "2026-10-06T08:00:00.123Z",
  "data": {
    "action": "admin.user.status-changed",
    "outcome": "SUCCESS",
    "actor": { "type": "USER", "id": "5c1d…", "userType": "ADMIN", "roles": ["ADMIN"] },
    "target": { "type": "user", "id": "9f0e2a3b-…" },
    "request": {
      "requestId": "1f0c…",
      "method": "PUT",
      "path": "/api/v1/admin/users/9f0e2a3b-…/status",
      "ip": "10.0.0.12",
      "userAgent": "Mozilla/5.0 …"
    },
    "details": { "id": "9f0e2a3b-…", "status": "SUSPENDED" },
    "occurredAt": "2026-10-06T08:00:00.123Z",
    "service": "user-management-service"
  }
}
```

A failed attempt:

```json
"data": {
  "action": "auth.login",
  "outcome": "FAILURE",
  "errorCode": "INVALID_CREDENTIALS",
  "actor": { "type": "ANONYMOUS" },
  "target": { "type": "user" },
  "request": { "method": "POST", "path": "/api/v1/auth/login", "ip": "203.0.113.7", … },
  "details": { "identifier": "sara@example.com", "password": "***" },
  …
}
```

### Fields of `data`

| Field | Description |
|---|---|
| `action` | What happened (table above) |
| `outcome` | `SUCCESS`, `FAILURE` (attempted but failed) or `DENIED` (blocked by security) |
| `errorCode` | When not `SUCCESS`: the API error code (see [API.md](API.md#errors)) |
| `actor.type` | `USER` (authenticated), `ANONYMOUS` (e.g. someone signing in) or `SYSTEM` |
| `actor.id`, `actor.userType`, `actor.roles` | From the access token, for `USER` |
| `target` | `{type, id}` of the object acted upon. For a sign-in, the `id` is only known on success |
| `request` | `requestId` (same as the `X-Request-Id` header and the service logs), method, path, client IP, user agent. Absent for `SYSTEM` |
| `details` | The request body, path variables and query parameters. Secrets are replaced by `***` |
| `occurredAt`, `service` | When, and which service produced it |

### Privacy and secrets

Values of `password`, `currentPassword`, `newPassword`, `code`, `refreshToken`, `accessToken`, `token` and `secret` are
replaced with `***` at any depth. Extend the list with `app.audit.redacted-fields`. Details may still contain
personal data (emails, phone numbers, names in profile updates), as audit logs usually must. Protect the topic
accordingly (ACLs, retention).

Client IPs come from the connection, or from `X-Forwarded-For` when the service runs behind a trusted proxy
(`server.forward-headers-strategy=framework`).

## Configuration

| Setting | Env var | Default |
|---|---|---|
| `app.audit.enabled` | `AUDIT_ENABLED` | `true` |
| `app.audit.redacted-fields` | | see above |
| `app.events.kafka.enabled` | `KAFKA_ENABLED` | `false` (`true` in docker-compose) |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `app.events.kafka.topics.audit` | `KAFKA_TOPIC_AUDIT` | `user-management.audit` |
| `app.events.kafka.topics.domain-events` | `KAFKA_TOPIC_DOMAIN_EVENTS` | `user-management.domain-events` |
| `app.events.kafka.create-topics` | `KAFKA_CREATE_TOPICS` | `true` (turn off where topics are managed centrally) |
| `app.events.kafka.partitions` / `replication-factor` | | `3` / `1` |

Kafka security (SASL, TLS) uses the standard Spring Boot properties, e.g. `SPRING_KAFKA_SECURITY_PROTOCOL`,
`SPRING_KAFKA_PROPERTIES_SASL_MECHANISM`, `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG`.

To publish only the audit log to Kafka, remove `domain-events` from `app.events.kafka.topics`. The streams a sink
doesn't handle go to the other sinks or to the log.

## Auditing a new endpoint

Add the annotation and you're done:

```java
@Audited(action = "admin.user.impersonated", target = "user")   // target id = the {id} path variable
@PostMapping("/{id}/impersonate")
public TokenResponse impersonate(@PathVariable UUID id) { … }
```

For activities that aren't HTTP calls (a scheduled job, a security reaction), inject `AuditLog`:

```java
auditLog.record("security.suspicious-login", AuditOutcome.DENIED, "INVALID_CREDENTIALS",
        AuditLog.target("user", userId), Map.of("reason", "impossible travel"));
```

## Consuming it (audit microservice sketch)

```java
@KafkaListener(topics = "user-management.audit", groupId = "audit-log")
void on(String envelope) {
    JsonNode message = json.readTree(envelope);
    if (repository.existsById(message.get("id").asText())) {
        return;                                    // at-least-once: ignore duplicates
    }
    repository.save(AuditEntry.from(message));     // store data.* in an append-only table / index
}
```

Use one consumer group per audit-service deployment. To rebuild its store, reset the group's offsets to the start of
the topic (keep the topic's retention long enough, or enable tiered storage).
