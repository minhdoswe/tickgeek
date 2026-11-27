# Data Model: Email Code Sign-In: Send the Code Email

**No schema change.** This feature writes columns that already exist in
`verification_codes` and `outboxes` (001 [data-model.md](../001-request-code/data-model.md),
migrations V1–V4). Design reasons are in [research.md](research.md).

## Columns this feature writes

| Table | Column | Written when |
|---|---|---|
| `outboxes` | `attempts` | claim: +1, committed before the send (R2) |
| `outboxes` | `status` | success → `SENT`, give up → `ERROR`, cancel → `CANCELLED` |
| `outboxes` | `published_at` | success: the time the provider accepted |
| `outboxes` | `next_attempt_at` | failed attempt with retries left: now + backoff (R6) |
| `outboxes` | `last_error` | failed attempt or give up: exception class and message, never the code |
| `outboxes` | `payload` | set to `NULL` on success, give up and cancel (FR-008) |
| `verification_codes` | `status` | delivered → `ACTIVE`, abandoned → `INVALIDATED` |
| `verification_codes` | `sent_at` | delivered: the accept time |
| `verification_codes` | `expires_at` | delivered: accept time + `tickgeek.auth.code-ttl` |

`verification_codes.attempt_count` counts wrong-code entries. This feature does not touch it.

## State changes

Every change applies only while its row is `PENDING`; otherwise it does nothing.

### Outbox

| Change | From → to | Fields |
|---|---|---|
| `claim()` | `PENDING` → `PENDING` | `attempts++` |
| `succeed(now)` | `PENDING` → `SENT` | `published_at = now`, `payload = NULL` |
| `retryLater(now, error, baseDelay)` | `PENDING` → `PENDING` | `last_error = error`, `next_attempt_at = now + baseDelay × 2^(attempts − 1)` |
| `giveUp(error)` | `PENDING` → `ERROR` | `last_error = error`, `payload = NULL` |
| `cancel()` | `PENDING` → `CANCELLED` | `payload = NULL` |

```text
             claim / retryLater
               ┌──────┐
               ▼      │
(new) ──▶ PENDING ────┘
            │ │ │
  succeed ──┘ │ └── cancel ──▶ CANCELLED
     ▼        │
   SENT     giveUp
              ▼
            ERROR
```

### Verification code (one code, by id)

| Change | From → to | Fields |
|---|---|---|
| `activatePending(id, now, now + code-ttl)` | `PENDING` → `ACTIVE` | `sent_at = now`, `expires_at = now + code-ttl` |
| `invalidatePending(id)` | `PENDING` → `INVALIDATED` | none |

Both are conditional `UPDATE … WHERE id = :id AND status = 'PENDING'` repository methods. The
database enforces the "only if PENDING" rule, so a code replaced at the same moment is left
alone (research R3). They sit beside the existing `invalidateLive(email)` (001 R4), which only
the code request calls.

### Combined, one transaction each

| Name | Outbox | Code | Used by |
|---|---|---|---|
| deliver | `succeed(now)` | `activatePending` | the provider accepted |
| abandon | `giveUp(error)` | `invalidatePending` | last attempt failed, or the claim found `attempts >= max` |

## In-process job event (not stored)

| Event | Fields | Published by |
|---|---|---|
| `OutboxJobScheduled` | `outboxId` (UUID), `delay` (Duration) | `CodeEmailQueue.enqueue` (delay 0); `OutboxJobSteps.recordFailure` when retries are left (delay = backoff) |

Both are published inside a transaction and acted on after it commits (research R1).

## Configuration values

New, `application.yaml` (`tickgeek.email`, `EmailProperties`):

| Key | Default | Meaning |
|---|---|---|
| `tickgeek.email.max-attempts` | 5 | attempts in total: the first plus 4 retries (FR-004) |
| `tickgeek.email.retry-base-delay` | 5s | wait before the first retry; it doubles after each failure |

Removed: `tickgeek.auth.verification-code-max-attempt-count` (draft only, research R8).
Used: `tickgeek.auth.code-ttl` (10m) for `expires_at`.
