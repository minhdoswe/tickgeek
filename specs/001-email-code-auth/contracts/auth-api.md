# Auth API Contract

Base path: `/api/auth`. JSON in and out. Called only by the Next.js server
(`tickgeek_frontend/lib/api/auth.ts`). Next.js sets `X-Forwarded-For` to exactly one canonical
client IP derived from the hosting proxy's header, replacing any client-sent value. The backend
trusts it only from the Next.js server address (research R6).

## Access rules

| Endpoint | Access |
|---|---|
| `POST /code-requests`, `POST /code-verifications`, `POST /registrations` | Anonymous |
| `POST /token-refreshes` | Anonymous; requires a valid refresh token in the body |
| `POST /sign-out` | Anonymous; revokes the refresh token in the body if it is valid |
| `GET /me` and every other `/api/**` path | `Authorization: Bearer <accessToken>` (valid JWT) |

## Token pair

Every successful sign-in, registration, and refresh returns tokens in this shape:

```json
{
  "accessToken": "<JWT, HS256, 15 min>",
  "accessTokenExpiresAt": "2026-11-11T10:15:00Z",
  "refreshToken": "<opaque, 256-bit>"
}
```

A refresh response returns the **same** refresh token; V1 does not rotate them (research R1).
JWT claims: `iss=tickgeek`, `sub=<user id>`, `iat`, `exp`.

## Error body (all endpoints)

```json
{
  "error": "CODE_INVALID",
  "message": "That code is incorrect. You have 3 tries left.",
  "fields": { "email": ["must be a valid email"] }
}
```

`fields` appears only for `VALIDATION_FAILED`. Messages are user-facing English (FR-015,
FR-024) and never reveal whether an account exists.

| error | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Malformed input (bad email, code not 6 digits, empty name) |
| `TOO_MANY_REQUESTS` | 429 | A code-request limit was hit; `Retry-After` header set for the resend cooldown only |
| `CODE_INVALID` | 422 | Wrong code; attempts remain |
| `CODE_EXPIRED` | 410 | No usable code: expired, replaced by a newer one, already used, or never requested |
| `CODE_LOCKED` | 410 | The code was invalidated after 5 wrong attempts; request a new one |
| `REGISTRATION_EXPIRED` | 410 | Registration permission missing, used, or expired |
| `UNAUTHENTICATED` | 401 | Missing, expired, or invalid access token (JWT) |
| `SESSION_EXPIRED` | 401 | Refresh token missing, revoked, or unused for 30 days; sign in again |

---

## POST /api/auth/code-requests

Request a code for an email (FR-001–FR-004, FR-008, FR-020).

```json
{ "email": "Ana@Example.com " }
```

**202 Accepted** (identical for registered and unregistered emails):

```json
{
  "email": "ana@example.com",
  "codeValidMinutes": 10,
  "resendAvailableAt": "2026-11-11T10:01:00Z"
}
```

There is no `expiresAt`, because the 10 minutes start only when the email provider accepts the
email (research R16).

**429** `TOO_MANY_REQUESTS`: for the resend cooldown, the message says how many seconds to
wait and `Retry-After` is set. For all other limits it says "Too many requests. Please try again
later." and `Retry-After` is not set, so the response does not reveal when a limit resets
(decided 2026-11-16).

**400** `VALIDATION_FAILED`.

## POST /api/auth/code-verifications

Check a code (FR-005, FR-007, FR-009, FR-010, FR-019).

```json
{ "email": "ana@example.com", "code": "042317" }
```

**200 OK**, existing account:

```json
{ "outcome": "SIGNED_IN", "tokens": { "accessToken": "…", "accessTokenExpiresAt": "…", "refreshToken": "…" } }
```

**200 OK**, no account yet:

```json
{
  "outcome": "REGISTRATION_REQUIRED",
  "registrationToken": "<opaque>",
  "registrationExpiresAt": "2026-11-11T10:10:00Z"
}
```

**422** `CODE_INVALID` (attempt counted; the 5th wrong attempt returns `CODE_LOCKED` instead).
**410** `CODE_EXPIRED` or `CODE_LOCKED`. **400** `VALIDATION_FAILED`
for a code that is not exactly 6 digits (attempt not counted).

## POST /api/auth/registrations

Complete the name step (FR-018, FR-019, FR-021).

```json
{ "registrationToken": "<opaque>", "firstName": "Ana", "lastName": "Nguyen" }
```

**200 OK**: a [token pair](#token-pair). Returned both when this request created the
account and when a concurrent flow created it first.

**410** `REGISTRATION_EXPIRED`. **400** `VALIDATION_FAILED`.

## POST /api/auth/sign-out

```json
{ "refreshToken": "<opaque>" }
```

Revokes this device's refresh token only (FR-013). The current access token is not revoked and
stays valid until it expires (≤ 15 min). The frontend deletes both cookies, so the device is
signed out immediately.

**204 No Content**. Also returned when the token is unknown or already revoked, so sign-out is
idempotent.

## POST /api/auth/token-refreshes

Get a new access token (FR-014). This is the only call that records activity.

```json
{ "refreshToken": "<opaque>" }
```

**200 OK**: a [token pair](#token-pair) with the same refresh token. `last_used_at` is set to
now. **401** `SESSION_EXPIRED`.

## GET /api/auth/me

Header `Authorization: Bearer <accessToken>`. Checked statelessly: signature, issuer, and
expiry only; no database lookup for authentication.

**200 OK**:

```json
{ "id": 42, "email": "ana@example.com", "firstName": "Ana", "lastName": "Nguyen" }
```

**401** `UNAUTHENTICATED`.

---

## Frontend routes (UI contract)

| Route | Shows | Notes |
|---|---|---|
| `/sign-in?next=/path` | Email form | `next` kept only if it is a TickGeek path (FR-022) |
| `/sign-in/code` | Code form, "sent to <email>", resend with countdown, change email | Email prefilled when going back (US3 #4) |
| `/sign-in/name` | First/last name form | Only with a valid registration cookie; otherwise redirects to `/sign-in` |
| `/dashboard` | Organizer home page (placeholder) | Protected; default landing page |

Cookies set by Next.js (all `HttpOnly`, `Secure` outside localhost, `SameSite=Lax`, `Path=/`):

| Cookie | Holds | Lifetime |
|---|---|---|
| `tg_access` | access token (JWT) | until `accessTokenExpiresAt` (15 min) |
| `tg_refresh` | refresh token | 30 days; re-set with a fresh 30 days on every refresh; both cookies cleared on `SESSION_EXPIRED` or sign-out |

`proxy.ts` refreshes the access token before rendering a protected page if `tg_access` is
missing or expires within 60 s (research R2).
| `tg_registration` | registration token | until `registrationExpiresAt` |
| `tg_sign_in` | email + `next` for the current attempt | 10 minutes |
