# User Management Service

A reusable, domain-agnostic user management microservice built with **Java 21** and **Spring Boot 4**.

Drop it into any product (logistics, e-commerce, health, education...) and describe your kinds of users
in a YAML file. You don't need to write code or run a database migration to add a "driver licence number" or a
"store name".

```
                ┌────────────────────────── user-management-service ──────────────────────────┐
  web / mobile  │  register · verify (OTP) · login · OTP login · refresh · reset password       │
  ───────────▶  │  my profile · admin users · roles & permissions · user-type schemas         │
                │                                                                             │
                │  core identity (users) + JSONB attributes validated by user-type schema     │
                └────────┬─────────────────────────────┬─────────────────────────────┬────────┘
                         │ RS256 JWT + /.well-known/jwks.json                         │ outbox events
                         ▼                             ▼                             ▼ (webhook / custom sink)
                   other services validate tokens offline        other services react to user.registered, ...
```

## Features

| Area | What you get |
|---|---|
| **Registration** | Self-service and admin-created users, per **user type** (e.g. `DRIVER`, `SHIPPER`), each with its own required contact channels, verification rule, default roles and **attribute schema** |
| **Domain attributes** | Declared in YAML: `STRING`, `INTEGER`, `NUMBER`, `BOOLEAN`, `DATE`, `ENUM`, `EMAIL`, `PHONE`, plus `required`, `pattern`, `min/max`, `min/max-length`, and write access (`READ_WRITE`, `WRITE_ONCE`, `ADMIN_ONLY`) |
| **Authentication** | Sign in with username, email or phone + password; passwordless sign-in with a one-time code; RS256 access tokens; rotating refresh tokens with **re-use detection**; logout and "sign out everywhere" |
| **OTP** | Email and phone verification, password reset, sign-in. Codes are hashed, expire, allow a limited number of attempts and have a resend cooldown |
| **Profiles** | `GET/PATCH/DELETE /users/me`, change password; changing email/phone triggers re-verification |
| **Administration** | Search (free text, type, role, status, attribute filters), create, update, suspend/activate, unlock, delete, assign roles |
| **Roles & permissions** | Declared in YAML (seeded at startup) and manageable at runtime; roles and permissions are embedded in the token |
| **Integration** | JWKS endpoint for other services; **transactional outbox** delivering `user.*` events to a signed webhook or any custom sink |
| **Security** | BCrypt, account lockout, no user enumeration on reset/OTP endpoints, timing-safe login, GDPR-style erasure on delete, privilege-escalation guard on role assignment |
| **Operations** | PostgreSQL + Flyway, Docker image (non-root, layered), docker-compose, health/readiness probes, Prometheus metrics, JSON logs (`prod`), request IDs, OpenAPI/Swagger UI, GitHub Actions CI |

## Quick start

Requirements: Docker. For development without Docker, you also need JDK 21 and Maven 3.9.

```bash
docker compose up --build
```

| URL | What |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive API documentation |
| http://localhost:8025 | Mailpit inbox (verification and reset codes sent by email) |
| http://localhost:8080/actuator/health | Health |

The compose stack runs the **logistics example** (`config/examples/logistics.yml`) with an administrator
`admin@example.com` / `Admin-Passw0rd`.

Try it:

```bash
# 1. See which kinds of users exist and what they must provide
curl -s localhost:8080/api/v1/user-types | jq

# 2. Register a shipper
curl -s localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{
  "userType": "shipper", "email": "sara@example.com", "password": "Str0ng-Password",
  "attributes": {"companyName": "Blue Nile Trading", "businessType": "SME"}}' | jq

# 3. Read the code in Mailpit (http://localhost:8025), then confirm it
curl -s localhost:8080/api/v1/auth/verification/confirm -H 'Content-Type: application/json' \
  -d '{"identifier": "sara@example.com", "code": "123456"}'

# 4. Sign in and call an authenticated endpoint
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"identifier": "sara@example.com", "password": "Str0ng-Password"}' | jq -r .accessToken)
curl -s localhost:8080/api/v1/users/me -H "Authorization: Bearer $TOKEN" | jq
```

SMS codes (e.g. for drivers, who register with a phone number) are written to the service log until you plug in an
SMS provider: `docker compose logs -f user-management`.

### Run locally without the container

```bash
docker compose up -d postgres mailpit
SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025 \
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD=Admin-Passw0rd \
APP_DOMAIN_CONFIG=./config/examples/logistics.yml \
./mvnw spring-boot:run
```

### Tests

```bash
./mvnw verify
```

The integration tests start PostgreSQL with Testcontainers and exercise every flow over HTTP, so Docker must
be running.

## Adapting it to your domain

1. Copy `config/examples/logistics.yml` to `config/domain.yml` (or anywhere else, then set `APP_DOMAIN_CONFIG`).
2. Declare your **roles** and **user types** with their attributes.
3. Start the service. The configuration is validated at startup, and mistakes such as an unknown default role
   or an invalid regex stop the startup with a clear message.

```yaml
app:
  roles:
    driver:
      permissions: [shipments:read-assigned, shipments:update-status]
  user-types:
    user:
      enabled: false          # turn off the generic built-in type
    driver:
      display-name: Driver
      self-registration: true
      phone-required: true
      default-roles: [DRIVER]
      attributes:
        - name: licenseNumber
          type: STRING
          required: true
          pattern: "^[A-Z0-9-]{5,20}$"
          access: WRITE_ONCE
        - name: vehicleType
          type: ENUM
          required: true
          allowed-values: [MOTORCYCLE, VAN, TRUCK]
```

When configuration isn't enough, there are small, typed extension points (custom validation rules, extra token
claims, SMS/email providers and event sinks). See **[docs/EXTENDING.md](docs/EXTENDING.md)**.

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Design, packages, data model, main flows, security model |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every setting and environment variable, production checklist |
| [docs/EXTENDING.md](docs/EXTENDING.md) | How to plug the service into a domain and extend it with code |
| [docs/API.md](docs/API.md) | Endpoint reference, error format, token format, events |
| [docs/PLAN.md](docs/PLAN.md) | The plan and prompt the service was built from |

## Project layout

```
src/main/java/com/usermanagement
├── auth          sign-in, tokens (JWT + refresh), OTP sign-in, password reset, JWKS
├── user          core identity, registration, profile, verification, admin API
├── usertype      user-type registry and attribute schema validation
├── role          roles & permissions, seeding, admin API
├── otp           one-time passwords
├── notification  email / SMS delivery (pluggable)
├── events        domain events, transactional outbox, sinks
├── extension     public extension points (SPI)
├── config        security, OpenAPI, cross-cutting configuration
└── common        errors, persistence base class, web utilities
src/main/resources/db/migration   Flyway migrations
config/examples                   ready-made domain configurations
```
