# Plugging in and extending

There are three levels of customisation, from least to most effort:

1. **Configuration only.** Covers user types, attributes, roles, branding, policies and lifetimes. Most domains
   never need more. See [CONFIGURATION.md](CONFIGURATION.md).
2. **Extension points.** Implement a small interface as a Spring bean. Put such code in its own package (e.g.
   `com.usermanagement.custom`) so that merging upstream improvements stays painless.
3. **Changing the core.** Rarely needed. See the last section.

---

## 1. Using the service from your other microservices

### Validate access tokens

Every service in your system can verify tokens offline. In Spring Boot:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://user-management:8080/.well-known/jwks.json
```

To use the `roles` and `permissions` claims for authorisation:

```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Optional.ofNullable(jwt.getClaimAsStringList("roles")).orElse(List.of())
                .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        Optional.ofNullable(jwt.getClaimAsStringList("permissions")).orElse(List.of())
                .forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return authorities;
    });
    return converter;
}

// then, e.g.
@PreAuthorize("hasAuthority('shipments:assign')")
```

In other stacks, any JWT library that supports JWKS and RS256 works (e.g. `jose` for Node.js or `PyJWT` with
`PyJWKClient` for Python). Check `iss` against `JWT_ISSUER`.

### Keep domain data in sync with events

If your shipment service needs to know when a driver registers or gets suspended, configure a webhook:

```bash
APP_EVENTS_WEBHOOK_URL=http://shipments:8080/internal/user-events
APP_EVENTS_WEBHOOK_SECRET=a-long-random-secret
```

Each event is POSTed as:

```json
{
  "id": "4b3c…",                    // unique, use it to de-duplicate (delivery is at-least-once)
  "type": "user.registered",
  "aggregateType": "user",
  "aggregateId": "9f0e…",           // user id
  "occurredAt": "2026-10-06T08:00:00Z",
  "data": { "user": { "id": "9f0e…", "userType": "DRIVER", "attributes": { … }, … }, "occurredAt": "…" }
}
```

with headers `X-Event-Id`, `X-Event-Type` and `X-Signature: sha256=<hex HMAC-SHA256 of the raw body>`. Verify the
signature before trusting the payload. Any non-2xx response is retried with exponential backoff.

To publish to a broker instead, implement an `EventSink` (it replaces the logging fallback):

```java
@Component
class KafkaEventSink implements EventSink {

    private final KafkaTemplate<String, String> kafka;

    KafkaEventSink(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(OutboxMessage message) throws Exception {
        // Keyed by user id so events for one user stay ordered within a partition.
        kafka.send("user-events", message.aggregateId(), message.payload()).get(10, TimeUnit.SECONDS);
    }
}
```

---

## 2. Extension points

### Custom registration rules: `RegistrationValidator`

Use this for rules that YAML can't express. It runs after built-in validation, for self-service and admin
registrations alike.

```java
@Component
class DriverAgeValidator implements RegistrationValidator {

    @Override
    public void validate(RegistrationContext context) {
        if (!context.userType().code().equals("DRIVER")) {
            return;
        }
        LocalDate born = LocalDate.parse((String) context.attributes().get("dateOfBirth"));
        if (born.plusYears(21).isAfter(LocalDate.now())) {
            throw ApiException.validation("attributes.dateOfBirth", "drivers must be at least 21 years old");
        }
    }
}
```

Need a domain-wide uniqueness rule (e.g. one account per tax id)? Inject `UserRepository` and query the JSONB column
with a native query, or call your domain service.

### Extra token claims: `TokenClaimsCustomizer`

```java
@Component
class TenantClaim implements TokenClaimsCustomizer {

    @Override
    public void customize(UserSnapshot user, Map<String, Object> claims) {
        Object tenant = user.attributes().get("tenantId");
        if (tenant != null) {
            claims.put("tenant_id", tenant);
        }
    }
}
```

Reserved claims (`sub`, `iss`, `exp`, `roles`, `permissions`, `user_type`, ...) can't be overridden.

### SMS or email provider: `NotificationSender`

Without a real sender, SMS messages are only logged. Adding a bean for a channel replaces the fallback:

```java
@Component
class TwilioSmsSender implements NotificationSender {

    private final RestClient twilio;   // configured with your account SID / token
    private final String from;

    TwilioSmsSender(RestClient.Builder builder, @Value("${twilio.account-sid}") String sid,
                    @Value("${twilio.auth-token}") String token, @Value("${twilio.from}") String from) {
        this.twilio = builder.baseUrl("https://api.twilio.com/2010-04-01/Accounts/" + sid)
                .defaultHeaders(headers -> headers.setBasicAuth(sid, token)).build();
        this.from = from;
    }

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public void send(Notification notification) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", notification.recipient());
        form.add("From", from);
        form.add("Body", notification.body());
        twilio.post().uri("/Messages.json").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().toBodilessEntity();
    }
}
```

Email goes through SMTP as soon as `spring.mail.host` is set. Implement a `NotificationSender` for `Channel.EMAIL`
to use a provider API (SES, SendGrid...) or HTML templates instead.

### Reacting in-process: Spring events

All user events are ordinary Spring application events, so you can also listen to them inside the service:

```java
@Component
class WelcomeEmail {

    @TransactionalEventListener   // after commit
    void on(UserRegisteredEvent event) { … }
}
```

---

## 3. Changing the core

The code is organised so that common changes stay local:

| I want to... | Change |
|---|---|
| Add a column to every user (e.g. `locale`) | New Flyway migration `V2__add_locale.sql`, the field on `User`, `UserResponse`, the request records and `ProfileUpdate`. Prefer an attribute if only some domains need it |
| Add an endpoint | A controller in the relevant feature package. Business logic goes in the service, and errors are thrown as `ApiException` with an `ErrorCode` |
| Add an error | A constant in `ErrorCode` (HTTP status + default message) |
| Add a permission check | `@PreAuthorize("hasAuthority('my:permission')")`, and declare the permission on a role in YAML |
| Add a one-time code use case | A value in `OtpPurpose`, then `OtpService.issue/verify` |
| Add a domain event | A record implementing `DomainEvent`, published from a transactional service method. The outbox does the rest |
| Change how users are searched | `UserSearchCriteria.toSpecification()` |

Run `mvn verify` after changes. The integration tests cover every public flow against a real PostgreSQL.
