# Auth API Contract

Base path: `/api/auth`. JSON in and out. Called only by the Next.js server
(`tickgeek_frontend/lib/api/auth.ts`). Next.js sets `X-Forwarded-For` to exactly one client IP
derived from the hosting proxy's header, replacing any client-sent value. The backend trusts it
only from the Next.js server address (research R6).

## Access rules

| Endpoint | Access |
|---|---|
| `POST /api/auth/code-requests` | Anonymous |
| Every other path or method | Denied: 403 |

## Error body

```json
{
  "error": "VALIDATION_FAILED",
  "message": "Please check the highlighted fields.",
  "fields": { "email": ["Enter a valid email address, like name@example.com."] }
}
```

`fields` appears only for field validation errors. Messages are user-facing English (FR-015,
FR-024) and never reveal whether an account exists.

| error | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bad email (with `fields`), or a body that is not valid JSON (no `fields`) |
| `TOO_MANY_REQUESTS` | 429 | A code-request limit was hit; `Retry-After` set for the resend cooldown only |

---

## POST /api/auth/code-requests

Request a code for an email (FR-001–FR-004, FR-006, FR-008, FR-020).

```json
{ "email": "Ana@Example.com " }
```

The email is trimmed and lowercased before validation: not blank, a valid email, at most 254
characters.

**202 Accepted** (identical for every email):

```json
{
  "email": "ana@example.com",
  "codeValidMinutes": 10,
  "resendAvailableAt": "2026-11-11T10:01:00Z"
}
```

`resendAvailableAt` is the request time plus the resend cooldown. There is no `expiresAt`,
because the 10 minutes start only when the email is sent (research R16).

**429** `TOO_MANY_REQUESTS`:
- Resend cooldown: "Please wait 45 seconds before requesting another code." with
  `Retry-After: 45`.
- Any other limit: "Too many requests. Please try again later." with no `Retry-After`, so the
  response does not reveal which limit was hit or when it resets.
- The same generic response when another request for the same email still holds that email's
  lock after 2 seconds (`lock_timeout`, research R5).

**400** `VALIDATION_FAILED`, with one of these messages under `fields.email`:
- "Enter your email address."
- "Enter a valid email address, like name@example.com."
- "Email addresses can have at most 254 characters."

---

## Frontend routes (UI contract)

| Route | Shows | Notes |
|---|---|---|
| `/` | — | Redirects to `/sign-in` |
| `/sign-in` | Email form | On success shows "We sent a 6-digit code to {email}. It is valid for {codeValidMinutes} minutes." on the same page; errors show under the field or below the form |

The email form checks for an empty or malformed email in the browser before calling the
backend. No cookies are set.
