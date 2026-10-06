# API reference

The live, interactive reference is served by the application at **`/swagger-ui.html`** (OpenAPI document at
`/v3/api-docs`). This page summarises the endpoints and conventions.

All endpoints are under `/api/v1` and exchange JSON. Authenticated endpoints expect
`Authorization: Bearer <accessToken>`.

## Endpoints

### Public

| Method | Path | Description |
|---|---|---|
| GET | `/user-types` | Enabled user types with their attribute schemas, for building forms |
| GET | `/user-types/{code}` | One user type |
| POST | `/auth/register` | Self-registration → `201` user |
| POST | `/auth/verification/request` | Send a verification code to `{identifier}` (email or phone) → `202` |
| POST | `/auth/verification/confirm` | `{identifier, code}` → marks the contact verified, activates pending accounts |
| POST | `/auth/login` | `{identifier, password}` → tokens. The identifier can be a username, email or phone |
| POST | `/auth/otp/request` | Send a sign-in code to `{identifier}` → `202` |
| POST | `/auth/otp/verify` | `{identifier, code}` → tokens |
| POST | `/auth/token/refresh` | `{refreshToken}` → new tokens (the refresh token is rotated) |
| POST | `/auth/logout` | `{refreshToken}` → ends that session (`204`) |
| POST | `/auth/password/forgot` | Send a reset code to `{identifier}` → `202` |
| POST | `/auth/password/reset` | `{identifier, code, newPassword}` → `204`, signs out everywhere |
| GET | `/.well-known/jwks.json` | Public key(s) for verifying access tokens |

### Authenticated user

| Method | Path | Description |
|---|---|---|
| GET | `/users/me` | My profile |
| PATCH | `/users/me` | Update names, username, email, phone and attributes (partial) |
| POST | `/users/me/password` | `{currentPassword, newPassword}`, signs out everywhere |
| DELETE | `/users/me/sessions` | Sign out everywhere |
| DELETE | `/users/me` | Delete my account (personal data is erased) |

### Administration

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/admin/users` | `users:read` | Search: `q`, `status`, `userType`, `role`, `attr=name:value` (repeatable), `page`, `size`, `sort` |
| GET | `/admin/users/{id}` | `users:read` | Get a user |
| POST | `/admin/users` | `users:write` (+ `roles:write` to pass `roles`) | Create a user of any type. `password` is optional |
| PATCH | `/admin/users/{id}` | `users:write` | Update any field, including `ADMIN_ONLY` attributes and the `emailVerified` / `phoneVerified` flags |
| PUT | `/admin/users/{id}/status` | `users:write` | `{status: ACTIVE \| SUSPENDED}` |
| PUT | `/admin/users/{id}/roles` | `users:write` + `roles:write` | `{roles: [...]}` replaces the user's roles |
| POST | `/admin/users/{id}/unlock` | `users:write` | Clear a sign-in lockout |
| DELETE | `/admin/users/{id}` | `users:write` | Delete (erase) a user |
| GET | `/admin/roles` | `roles:read` | List roles |
| GET | `/admin/roles/{name}` | `roles:read` | Get a role |
| POST | `/admin/roles` | `roles:write` | `{name, description, permissions}` |
| PUT | `/admin/roles/{name}` | `roles:write` | Replace description and permissions |
| DELETE | `/admin/roles/{name}` | `roles:write` | Only for roles that aren't system roles and aren't assigned |

### Operations

`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`,
`/actuator/prometheus`.

## Partial updates

`PATCH` bodies follow one rule: **omitted means unchanged**. For optional text fields, an empty string clears the
value. Inside `attributes`, keys you leave out are kept and a `null` value removes the attribute:

```json
PATCH /api/v1/users/me
{ "firstName": "Sara", "phone": "", "attributes": { "vehicleType": "TRUCK", "vehicleCapacityKg": null } }
```

Changing `email` or `phone` resets its verification flag and sends a code to the new value.

## Tokens

```json
{
  "accessToken": "eyJraWQiOi…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "kq3…",
  "refreshExpiresIn": 2592000,
  "user": { "id": "…", "userType": "DRIVER", "status": "ACTIVE", "roles": ["DRIVER"], "attributes": { … }, … }
}
```

Access token claims:

| Claim | Example |
|---|---|
| `iss`, `sub`, `iat`, `exp`, `jti` | standard; `sub` is the user id |
| `user_type` | `"DRIVER"` |
| `roles` | `["DRIVER"]` |
| `permissions` | `["shipments:read-assigned", "shipments:update-status"]` |
| `preferred_username`, `email`, `email_verified`, `phone_number`, `phone_number_verified` | when present |
| custom | from `TokenClaimsCustomizer` beans |

## Errors

Every error is an RFC 7807 problem (`application/problem+json`) with a stable `code`:

```json
{
  "type": "urn:problem-type:validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "code": "VALIDATION_FAILED",
  "errors": {
    "attributes.licenseNumber": "is required",
    "attributes.vehicleType": "must be one of [MOTORCYCLE, VAN, TRUCK]"
  },
  "requestId": "1f0c…",
  "timestamp": "2026-10-06T08:00:00Z"
}
```

| Code | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | See `errors` (keys are field paths) |
| `PASSWORD_POLICY_VIOLATION` | 400 | The password doesn't meet the policy |
| `INVALID_OTP` | 400 | Wrong, expired or already used code |
| `UNKNOWN_USER_TYPE` | 400 | The user type doesn't exist or is disabled |
| `INVALID_CREDENTIALS` | 401 | Wrong identifier or password |
| `INVALID_TOKEN` | 401 | Invalid, expired or revoked refresh token |
| `UNAUTHORIZED` | 401 | Missing or invalid access token |
| `ACCESS_DENIED` | 403 | Missing permission |
| `ACCOUNT_NOT_VERIFIED` | 403 | Verify the email or phone first |
| `ACCOUNT_DISABLED` | 403 | The account is suspended |
| `REGISTRATION_NOT_ALLOWED` | 403 | The type is admin-created only |
| `FEATURE_DISABLED` | 403 | e.g. OTP sign-in is turned off |
| `USER_NOT_FOUND`, `ROLE_NOT_FOUND`, `USER_TYPE_NOT_FOUND`, `NOT_FOUND` | 404 | |
| `DUPLICATE_IDENTIFIER` | 409 | `errors` names the conflicting field(s) |
| `ROLE_ALREADY_EXISTS`, `ROLE_IN_USE`, `SYSTEM_ROLE` | 409 | |
| `CONCURRENT_MODIFICATION`, `DATA_CONFLICT` | 409 | Retry the request |
| `ACCOUNT_LOCKED` | 423 | Too many failed sign-ins. Wait, reset the password, or ask an admin to unlock |
| `OTP_ATTEMPTS_EXCEEDED`, `OTP_RESEND_TOO_SOON` | 429 | |
| `INTERNAL_ERROR` | 500 | Logged with the `requestId` |

Send an `X-Request-Id` header to correlate your logs with the service's. If you don't, the service generates one and
returns it in the response.

## Events

Domain events are published on the `domain-events` stream (Kafka topic `user-management.domain-events`). The activity
log is published on the `audit` stream and is described in [AUDIT.md](AUDIT.md).

| Type | Payload (`data`) |
|---|---|
| `user.registered` | `{user: UserSnapshot, occurredAt}` |
| `user.updated` | `{user: UserSnapshot, occurredAt}` |
| `user.verified` | `{userId, channel: EMAIL \| SMS, occurredAt}` |
| `user.status-changed` | `{userId, previousStatus, status, occurredAt}` |
| `user.roles-changed` | `{userId, roles, occurredAt}` |
| `user.password-changed` | `{userId, occurredAt}` |
| `user.deleted` | `{userId, occurredAt}` |

`UserSnapshot` = `{id, userType, username, email, phone, firstName, lastName, status, emailVerified, phoneVerified,
roles, attributes}`. See [EXTENDING.md](EXTENDING.md#keep-domain-data-in-sync-with-events) for the delivery
envelope and signature.
