# Plan & Build Prompt

This document records the plan and the prompt that the service was built from.
It explains *why* the service looks the way it does, so you can keep extending it
in the same spirit.

## 1. The prompt

> Build a **reusable, domain-agnostic user management microservice** in Java and
> Spring Boot. It must be usable "plug and play" by very different applications
> (e.g. a logistics app with drivers, shippers and dispatchers; an e-commerce app
> with customers and sellers) with as little code change as possible.
>
> **Functional scope**
> - Registration (self-service and admin-created) for multiple *user types*,
>   each with its own required/optional domain-specific fields.
> - Login with username, email or phone + password; passwordless OTP login.
> - JWT access tokens + rotating refresh tokens; logout / logout everywhere.
> - OTP for email/phone verification, password reset and login.
> - Profile read/update/delete for the current user; password change.
> - Role and permission management; admin user management (search, status,
>   roles).
> - Domain events so other services can react to user lifecycle changes.
>
> **Non-functional scope**
> - Production ready: PostgreSQL + Flyway, Docker image + docker-compose,
>   health checks, metrics, structured logs, OpenAPI docs, CI pipeline,
>   integration tests against a real database.
> - Secure defaults: BCrypt, account lockout, hashed OTPs and refresh tokens,
>   refresh-token reuse detection, no user enumeration, RS256 + JWKS.
> - Simple, robust architecture that is easy to understand, extend and modify.
>   Configuration over code; clear extension points where code is required.

## 2. Key design decisions

| Problem | Decision | Why |
|---|---|---|
| Different domains need different user fields | A fixed **core identity** (`users` table) plus a JSONB `attributes` column, validated against a **user-type schema declared in YAML** | No schema migration or code change to add a "driver licence number". The core stays the same across all domains. |
| Different domains have different kinds of users | **User types** (`app.user-types.*`) declare: self-registration allowed?, required contact channels, verification, default roles, attribute schema | A new domain is a new YAML file. |
| Clients need to render registration forms | Public `GET /api/v1/user-types` exposes the schemas | Frontends can build forms dynamically. |
| Domain-specific rules that YAML can't express | `RegistrationValidator` SPI (Spring bean) | One small class instead of forking the service. |
| Other services need to know about users | **Transactional outbox** + pluggable `EventSink` (log, webhook with HMAC signature; easy to add Kafka/RabbitMQ) | Reliable, at-least-once, no dual-write problem. |
| Other services need to trust our tokens | RS256 JWTs + `/.well-known/jwks.json` | Any resource server validates tokens offline with standard libraries. |
| Roles differ per domain | Roles + permissions declared in YAML, seeded at startup, editable at runtime via admin API | Plug-and-play defaults, runtime flexibility. |
| SMS/e-mail providers differ | `NotificationSender` SPI per channel; SMTP and logging implementations included | Swap in Twilio, SES, etc. by adding one bean. |

## 3. Architecture

Package-by-feature, each feature with a thin controller → service → repository
layering. Features only talk to each other through services and events.

```
com.usermanagement
├── auth          login, tokens (JWT + refresh), OTP login, password reset, JWKS
├── user          core identity, profile (/me), admin user management
├── usertype      user-type registry + attribute schema validation
├── role          roles & permissions, startup seeding, admin API
├── otp           one-time-password generation/verification
├── notification  delivery channels (email, sms) behind an SPI
├── events        domain events, transactional outbox, sinks (log/webhook)
├── extension     public SPI for domain customisation
├── config        typed configuration properties, security, OpenAPI
└── common        errors (RFC 7807), base entity, paging, utilities
```

## 4. Implementation steps

1. Maven project (Spring Boot 4.1, Java 21), Flyway schema, typed `AppProperties`.
2. Common building blocks: `ApiException` + `ErrorCode`, global ProblemDetail
   handler, `BaseEntity` with auditing + optimistic locking, `PageResponse`.
3. User types & attribute validation (pure Java, unit tested).
4. Roles & permissions with YAML seeding and admin API.
5. User aggregate, registration, profile, admin management with JPA
   Specifications for search.
6. OTP service (hashed codes, expiry, attempt limits, resend cooldown) and
   notification SPI.
7. Auth: JWT (RS256, JWKS), refresh-token rotation with reuse detection,
   lockout, OTP login, password reset.
8. Events: Spring events → outbox table → scheduled relay → sinks.
9. Security config, CORS, OpenAPI, actuator/Prometheus, correlation IDs,
   structured logging.
10. Tests: unit tests + Testcontainers integration tests of full flows.
11. Dockerfile (multi-stage, non-root, layered), docker-compose (Postgres,
    Mailpit), GitHub Actions CI.
12. Documentation: README, architecture, configuration, extension guide, API.
