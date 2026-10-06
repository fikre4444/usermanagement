# Configuration

Configuration comes in two layers:

1. **`src/main/resources/application.yml`** holds the defaults shared by every deployment. Values written as
   `${ENV_VAR:default}` can be overridden with environment variables.
2. **A domain file** (`APP_DOMAIN_CONFIG`, default `./config/domain.yml`) holds your user types, roles and branding.
   It is imported on top of the defaults. Maps such as `app.roles` and `app.user-types` are **merged**: you add
   entries, and you can change individual properties of built-in ones (e.g. `app.user-types.user.enabled: false`).

Any property can also be set through an environment variable using Spring's relaxed binding:
`app.events.webhook.url` → `APP_EVENTS_WEBHOOK_URL`.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/usermanagement` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `usermanagement` | Database credentials |
| `DB_POOL_SIZE` | `10` | Hikari maximum pool size |
| `PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | | `prod` for production hardening (see below) |
| `APP_DOMAIN_CONFIG` | `./config/domain.yml` | Path to your domain configuration (optional file) |
| `APP_NAME` | `User Management` | Product name used in emails/SMS |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | | First administrator, created at startup if missing |
| `JWT_ISSUER` | `user-management-service` | `iss` claim. Use your public URL |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | | PEM content or location (`file:/run/secrets/jwt-private.pem`) |
| `JWT_ACCESS_TOKEN_TTL` / `JWT_REFRESH_TOKEN_TTL` | `15m` / `30d` | Token lifetimes |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | | SMTP server. Without a host, emails are logged instead |
| `MAIL_FROM` | `no-reply@example.com` | Sender address |
| `APP_EVENTS_WEBHOOK_URL` / `APP_EVENTS_WEBHOOK_SECRET` | | Deliver events to a webhook, signed with HMAC-SHA256 |
| `CORS_ALLOWED_ORIGINS` | | Comma-separated browser origins allowed to call the API |
| `SWAGGER_ENABLED` | `true` | Expose `/swagger-ui.html` and `/v3/api-docs` |

## Domain configuration

### Roles

```yaml
app:
  roles:
    dispatcher:                       # key → role name DISPATCHER (upper-cased, '-' → '_')
      description: Assigns shipments to drivers
      permissions: [shipments:read, shipments:assign, users:read]
```

At startup, roles that don't exist yet are created and existing ones get any missing permissions. Permissions added at
runtime through the API are kept. Roles declared here are *system roles*, so they can't be deleted through the API.

Permissions are free-form strings. This service uses `users:read`, `users:write`, `roles:read` and `roles:write`.
Any other permission (`shipments:assign`) is simply carried in the access token for your other services.

### User types

```yaml
app:
  user-types:
    driver:                           # key → type code DRIVER
      display-name: Driver
      description: Independent drivers
      enabled: true                   # false: no new registrations; existing users keep working
      self-registration: true         # false: only admins can create users of this type
      email-required: false
      phone-required: true            # at least one of email/phone is always required
      verification-required: true     # self-registered users start PENDING_VERIFICATION
      default-roles: [DRIVER]         # must be declared under app.roles
      attributes:
        - name: licenseNumber         # letters, digits, '_' ; starts with a letter
          label: Driving licence number
          description: As printed on the licence
          type: STRING                # STRING | INTEGER | NUMBER | BOOLEAN | DATE | ENUM | EMAIL | PHONE
          required: true
          pattern: "^[A-Z0-9-]{5,20}$" # STRING only
          min-length: 5               # STRING only
          max-length: 20              # STRING only
          access: WRITE_ONCE          # READ_WRITE (default) | WRITE_ONCE | ADMIN_ONLY
        - name: vehicleCapacityKg
          type: NUMBER
          min: 0                      # INTEGER / NUMBER
          max: 40000
        - name: vehicleType
          type: ENUM
          allowed-values: [MOTORCYCLE, VAN, TRUCK]
```

**Attribute access:**

| `access` | At registration (user) | Later (user) | Admin |
|---|---|---|---|
| `READ_WRITE` | ✔ | ✔ | ✔ |
| `WRITE_ONCE` | ✔ | ✘ | ✔ |
| `ADMIN_ONLY` | ✘ | ✘ | ✔ |

`required` is enforced on creation when the creator is allowed to write the attribute, and whenever someone tries
to remove the value.

The whole configuration is validated at startup. Unknown default roles, duplicate attribute names, invalid regexes,
an `ENUM` without values or `min > max` all stop the startup with a message that lists every problem.

**Built-in types:** `admin` (no self-registration, role `ADMIN`, used by the bootstrap administrator) and `user`
(generic self-registration, role `USER`). Disable `user` if your domain doesn't need it.

Two complete examples are in [`config/examples`](../config/examples): `logistics.yml` and `ecommerce.yml`.

## Service settings (`app.*`)

| Property | Default | Description |
|---|---|---|
| `app.auth.max-failed-attempts` | `5` | Wrong passwords before a temporary lock |
| `app.auth.lock-duration` | `15m` | Lock duration |
| `app.auth.otp-login-enabled` | `true` | Allow passwordless sign-in with a one-time code |
| `app.otp.length` | `6` | Digits per code |
| `app.otp.ttl` | `10m` | Code lifetime |
| `app.otp.max-attempts` | `5` | Wrong guesses allowed per code |
| `app.otp.resend-cooldown` | `60s` | Minimum delay between two codes for the same purpose |
| `app.password-policy.min-length` / `max-length` | `8` / `128` | |
| `app.password-policy.require-uppercase` / `require-lowercase` / `require-digit` | `true` | |
| `app.password-policy.require-special-character` | `false` | |
| `app.jwt.allow-generated-key` | `true` (`false` in `prod`) | Generate a temporary key when none is configured |
| `app.jwt.key-id` | key thumbprint | `kid` header |
| `app.notification.log-content` | `true` (`false` in `prod`) | Whether the logging fallback prints message bodies (codes) |
| `app.events.relay.enabled` | `true` | Run the outbox relay |
| `app.events.relay.interval` | `PT5S` | Delay between relay runs |
| `app.events.relay.batch-size` | `100` | Events per relay transaction |
| `app.events.relay.max-attempts` | `10` | Attempts before an event is marked failed |
| `app.events.relay.initial-backoff` / `max-backoff` | `10s` / `1h` | Exponential retry backoff |
| `app.events.retention` | `7d` | How long delivered events are kept |
| `app.bootstrap.admin.enabled` | `true` | Create the first admin from `ADMIN_EMAIL` / `ADMIN_PASSWORD` |

## Production checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod`. This refuses to start without JWT keys, stops logging OTP codes and switches
  logs to JSON.
- [ ] Generate a key pair (`scripts/generate-jwt-keys.sh`), store it in your secret manager and give it to every
  instance through `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`.
- [ ] Set `JWT_ISSUER` to the service's public URL.
- [ ] Configure SMTP (`SPRING_MAIL_*`) and an SMS sender bean if you use phone numbers (see EXTENDING.md).
- [ ] Use a strong `ADMIN_PASSWORD`, then change it after the first sign-in or remove the variables.
- [ ] Put the service behind TLS and a gateway with per-IP rate limiting on `/api/v1/auth/**`.
- [ ] Don't expose `/actuator/prometheus` publicly (route it only internally), or set `SWAGGER_ENABLED=false` if the
  API docs should be private.
- [ ] Set `CORS_ALLOWED_ORIGINS` if browsers call the API directly.
- [ ] Back up PostgreSQL. All state lives there.
