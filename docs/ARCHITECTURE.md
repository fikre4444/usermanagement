# Architecture

## Goals

1. **One service, many domains.** The same build serves a logistics app, a marketplace or a clinic. What
   changes between domains is *configuration*, not code.
2. **Simple to read.** Package-by-feature, plain Spring components, no code generation or hidden magic.
   A newcomer should be able to follow a request from controller to database in a few minutes.
3. **Safe by default.** Every security-sensitive decision (hashing, lockout, token rotation, enumeration,
   privilege escalation) is handled in the service, not left to the consumer.
4. **Extensible at clear seams.** Where configuration is not enough, there is a small, typed interface to
   implement (see [EXTENDING.md](EXTENDING.md)).

## The core idea: a fixed identity plus a typed attribute bag

Every user, in every domain, shares the same **core identity**: id, user type, username, email, phone,
password, names, status, verification flags, roles, and audit data. This lives in normal columns, which keeps it
indexed, constrained and unique.

Everything domain-specific goes into a single **`attributes` JSONB column**. It is not schemaless: each
**user type** declares an attribute schema in YAML, and every write is validated against it.

```
users
├── id, user_type, username, email, phone, password_hash, first_name, last_name
├── status, email_verified, phone_verified, failed_login_attempts, locked_until, ...
└── attributes (jsonb)   ← {"licenseNumber": "DL-12345", "vehicleType": "VAN", "dateOfBirth": "1990-05-31"}
                            validated against app.user-types.driver.attributes
```

Why this design:

* **No migrations per domain.** You add a field by editing YAML.
* **No inheritance hierarchies or per-type tables** that every consumer would have to fork.
* **Still queryable.** A GIN index on `attributes` supports the admin `attr=name:value` filters.
* **Escape hatch.** If a domain needs heavy relational data (a driver's vehicles, documents...), it belongs in that
  domain's own service, keyed by the user id and kept in sync through [events](#events).

## Packages

```
com.usermanagement
├── auth          AuthService (password / OTP sign-in, refresh, logout, reset), TokenService (JWT),
│                 RefreshTokenService (rotation + reuse detection), JwtConfig (keys), JWKS endpoint
├── user          User entity, RegistrationService, UserService (profile/admin ops), VerificationService,
│                 UserLookup, Identifiers, PasswordPolicy, user events, web controllers
├── usertype      UserTypeRegistry (loads + validates YAML), AttributeValidator, public schema endpoint
├── role          Role entity, RoleService, RoleSeeder (YAML → DB), admin endpoint
├── otp           OtpService (issue/verify), OtpDeliveryListener (sends after commit)
├── notification  NotificationService routing to NotificationSender per channel (SMTP, logging fallback)
├── events        DomainEvent, OutboxWriter, OutboxRelay, EventSink (logging, webhook)
├── extension     Public SPI: RegistrationValidator, TokenClaimsCustomizer
├── config        SecurityConfig, OpenApiConfig, CoreConfig (clock, auditing, async, scheduling)
└── common        ApiException + ErrorCode + GlobalExceptionHandler, BaseEntity, CorrelationIdFilter, utils
```

Dependencies point in one direction: `auth → user → (usertype, role, otp) → notification`, and everything may publish
`events`. When a lower layer needs to trigger something in a higher one, it publishes an event. For example, a password
change in `user` publishes `UserPasswordChangedEvent`, and `auth` reacts by revoking refresh tokens.

Each feature follows the same shape: **controller** (HTTP + validation of the request shape) → **service**
(business rules, transactions) → **repository** (Spring Data JPA). Entities hold the rules about their own state
(e.g. `User.recordFailedLogin`, `User.markVerified`) so that services stay short.

## Data model

| Table | Purpose |
|---|---|
| `users` | Core identity + `attributes` JSONB. `email`, `phone`, `username` are unique (normalised: lower-case / E.164). |
| `roles`, `role_permissions` | Roles and their permission strings. `system_role` marks roles declared in YAML. |
| `user_roles` | Many-to-many between users and roles. |
| `refresh_tokens` | SHA-256 of each refresh token, its *family* (session), expiry and revocation time. |
| `otp_codes` | SHA-256 of each one-time code (bound to user, purpose and destination), expiry, attempts, consumption. |
| `outbox_events` | Domain events awaiting delivery, with retry bookkeeping. |

The schema is owned by Flyway (`src/main/resources/db/migration`). Hibernate only validates it
(`ddl-auto: validate`). All tables use UUID keys, optimistic locking (`version`) and audit timestamps.

## Main flows

### Registration and verification

```
POST /auth/register ─▶ RegistrationService.register (one transaction)
    1. resolve user type, check self-registration is allowed
    2. normalise + validate identifiers, contact rules, password policy
    3. validate attributes against the type's schema
    4. uniqueness check, then custom RegistrationValidator beans
    5. save user (PENDING_VERIFICATION if the type requires verification), assign default roles
    6. publish UserRegisteredEvent ─▶ OutboxWriter stores it in the same transaction
    7. OtpService.issue ─▶ code hash stored; OtpIssuedEvent
  commit
    └▶ OtpDeliveryListener (after commit, async) ─▶ NotificationService ─▶ email/SMS

POST /auth/verification/confirm ─▶ OtpService.verify ─▶ User.markVerified ─▶ ACTIVE
```

Codes are only sent **after commit**, so a failed registration never sends a code, and a slow SMS provider never
slows down the request.

### Sign-in and tokens

* **Access token**: an RS256 JWT, valid for 15 minutes by default. It carries `sub` (user id), `user_type`, `roles`,
  `permissions` and OIDC-style contact claims. Other services verify it offline using `/.well-known/jwks.json`.
* **Refresh token**: 256 random bits. Only its SHA-256 hash is stored, and it is valid for 30 days by default.
  Each refresh **rotates** it. Tokens from one sign-in form a *family*. If a token that was already rotated is
  presented again, it was probably stolen, so the whole family is revoked.
* **Lockout**: after `max-failed-attempts` wrong passwords, the account is locked for `lock-duration`. The lock is
  lifted by a password reset or an admin unlock.
* **Revocation**: password change/reset, suspension and deletion revoke every refresh token. This happens through
  events, in the same transaction. Access tokens expire on their own, so keep their TTL short.

### Events

```
service method (transaction) ── publishEvent(DomainEvent) ──▶ OutboxWriter ──▶ outbox_events row
                                                                       (same commit)
OutboxRelay (every 5 s, FOR UPDATE SKIP LOCKED) ──▶ EventSink(s) ──▶ published_at / retry with backoff
```

* **At-least-once** delivery. Consumers should de-duplicate on the event `id`.
* Several instances can relay concurrently because `SKIP LOCKED` keeps them from processing the same row twice.
* After `max-attempts` failures an event is marked `failed_at` and kept for inspection.
* Event types: `user.registered`, `user.updated`, `user.verified`, `user.status-changed`, `user.roles-changed`,
  `user.password-changed`, `user.deleted`. The payload is the event record serialised as JSON, and it never
  contains password hashes or codes.

## Security model

| Concern | Measure |
|---|---|
| Password storage | `DelegatingPasswordEncoder` (BCrypt), configurable policy |
| Brute force on passwords | Per-account lockout. Put per-IP rate limiting in your gateway |
| Brute force on codes | 6 digits, 10 min TTL, 5 attempts per code, 60 s resend cooldown, codes bound to purpose and destination |
| Account discovery | `password/forgot`, `otp/request` and `verification/request` always answer 202. Login returns the same error for unknown users and wrong passwords, and spends the same time hashing |
| Token theft | Short-lived access tokens, rotating refresh tokens with reuse detection, hashed at rest |
| Privilege escalation | Assigning roles requires both `users:write` and `roles:write`. Users can't write `ADMIN_ONLY` attributes or change `WRITE_ONCE` ones |
| Data erasure | Deleting a user erases personal data and frees its identifiers while keeping the id for referential integrity |
| Transport / browser | Stateless API (no cookies, so no CSRF surface), CORS allow-list, `X-Request-Id` correlation |

## Operational characteristics

* **Stateless.** Scale horizontally behind a load balancer. All instances need the same JWT key pair.
* **Scheduled jobs** (outbox relay, purging expired codes, tokens and delivered events) are safe to run on every
  instance.
* **Probes**: `/actuator/health/liveness`, `/actuator/health/readiness`. **Metrics**: `/actuator/prometheus`.
* **Logs**: plain text with request id in development. ECS JSON with the `prod` profile.
* **Virtual threads** are enabled for request handling.
