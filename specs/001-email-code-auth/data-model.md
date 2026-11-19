# Data Model: Email Code Sign-In and Registration

Delivered in Flyway migration `V1__create_ticket_system.sql`, merged into the base schema
(see [research.md](research.md) R15). All timestamps are `TIMESTAMPTZ`. Emails are always
stored normalized (trimmed, lowercase).

## users (altered)

| Column | Type | Rules |
|---|---|---|
| id | integer identity | PK (existing) |
| email | text | NOT NULL, UNIQUE (existing), ≤ 254 chars; new CHECK `email = lower(btrim(email))` |
| first_name | text | NOT NULL (existing), 1–50 chars after trim |
| last_name | text | NOT NULL (existing), 1–50 chars after trim |
| email_verified_at | timestamptz | NULL only for legacy V1 rows; set at creation for new accounts, and on a legacy user's first successful code verification |
| created_at | timestamptz | NOT NULL DEFAULT now() (new); legacy rows get the migration time |
| ~~password~~ | — | dropped (FR-017) |

New rows exist only after registration completes and are always verified.

**Migration of existing rows** (research R15), in order: (1) abort with an error listing
duplicates if any two rows share `lower(btrim(email))`; (2) normalize all emails; (3) add the
normalization CHECK; (4) backfill `created_at`; leave `email_verified_at` NULL; (5) drop
`password`.

## verification_codes (replaced)

| Column | Type | Rules |
|---|---|---|
| id | bigint identity | PK |
| email | text | NOT NULL, normalized |
| code_hmac | text | NOT NULL; HMAC-SHA256 of `email:code` (R3) |
| status | text | NOT NULL, CHECK in (`PENDING`, `ACTIVE`, `USED`, `INVALIDATED`) |
| attempt_count | integer | NOT NULL DEFAULT 0, CHECK 0–5 |
| request_source | text | NOT NULL; client IP or `unknown` (R6) |
| created_at | timestamptz | NOT NULL DEFAULT now(); request time, used for limits |
| sent_at | timestamptz | NULL until the provider accepts the email |
| expires_at | timestamptz | NULL while `PENDING`; `sent_at + 10 min` (R16); CHECK `status <> 'ACTIVE' OR expires_at IS NOT NULL` |
| used_at | timestamptz | NULL until verified |

Indexes: partial UNIQUE `(email) WHERE status IN ('PENDING', 'ACTIVE')`; `(email, created_at)`;
`(request_source, created_at)`; `(created_at)`.

State transitions:

```text
PENDING ──provider accepted email──────▶ ACTIVE (sent_at, expires_at set)
PENDING ──new code requested───────────▶ INVALIDATED
PENDING ──email failed after retries───▶ INVALIDATED
ACTIVE  ──correct code, not expired────▶ USED
ACTIVE  ──5th wrong code───────────────▶ INVALIDATED
ACTIVE  ──new code requested───────────▶ INVALIDATED
ACTIVE + expires_at ≤ now  ⇒ treated as expired (no status change needed)
```

Verification reads the `ACTIVE` row with `SELECT ... FOR UPDATE`, so wrong-attempt counting
and success are serialized per email (R4).

## registration_permissions (new)

| Column | Type | Rules |
|---|---|---|
| id | bigint identity | PK |
| email | text | NOT NULL, normalized |
| token_hash | text | NOT NULL, UNIQUE; SHA-256 of a 256-bit random token |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| expires_at | timestamptz | NOT NULL; equals the verified code's `expires_at` |
| used_at | timestamptz | NULL until registration completes |

States: active (`used_at IS NULL AND expires_at > now()`) → used, or → expired by time.

## refresh_tokens (new): the spec's "Session"

One row per device sign-in. Access tokens (JWT, 15 min) are not stored anywhere (research R1).

| Column | Type | Rules |
|---|---|---|
| id | bigint identity | PK |
| user_id | integer | NOT NULL, FK → users.id |
| token_hash | text | NOT NULL, UNIQUE; SHA-256 of a 256-bit random token |
| created_at | timestamptz | NOT NULL DEFAULT now() |
| last_used_at | timestamptz | NOT NULL; set at sign-in and on every successful refresh (R2) |
| revoked_at | timestamptz | NULL until sign-out |

Valid when `revoked_at IS NULL AND last_used_at > now() - interval '30 days'`.
A user may have many refresh tokens, one per device (FR-013). Index: `(user_id)`. Expired and
revoked rows are deleted by the daily cleanup job after 90 days.

```text
valid ──sign-out──────────────────────▶ revoked
valid ──30 days without a refresh─────▶ expired (by time)
```

## outboxes (altered)

Existing table, altered:

| Column | Change |
|---|---|
| payload | becomes nullable; erased (NULL) when the job reaches `SENT`, `CANCELLED`, or `ERROR` |
| verification_code_id | new, bigint NULL, FK → verification_codes.id; set for code emails |
| status | enum `outbox_status` gains `CANCELLED` |

A code email payload holds the recipient and the code. The expiry is not included because it
is set only when the email is accepted. The email sender sends only while the linked code is
`PENDING`; otherwise it cancels the job (R9).

Column meanings under the in-process design (R21, keeping R20's sweeper); no columns are added:

| Column | Meaning |
|---|---|
| next_attempt_at | When this job is due: the request time for a new job, `now + backoff` after a failed send (the retry scheduler runs it then), `now` when the sweeper re-dispatches it. A dispatch that arrives before this time is ignored. |
| attempts | Failed sends so far. Sets the next backoff (R19 formula, R21). |
| published_at | When the provider accepted the email (existing column; set on `SENT`). |

Index (new): `outboxes_pending_due_idx ON outboxes (next_attempt_at) WHERE status = 'PENDING'`,
for the sweeper (R20).

```text
PENDING ──linked code no longer PENDING──▶ CANCELLED (payload erased)
PENDING ──provider accepted──────────────▶ SENT (payload erased; code → ACTIVE)
PENDING ──failed send, attempts < 5──────▶ PENDING (attempts + 1; next_attempt_at = now + backoff;
                                                    retry scheduled in memory)
PENDING ──5th failed attempt─────────────▶ ERROR (payload erased; code → INVALIDATED)
PENDING ──dispatch lost (restart, full
          executor), overdue by
          stuck-after────────────────────▶ PENDING (next_attempt_at = now; re-dispatched)
```

Every transition from `PENDING` happens in the sending transaction, with the row locked
(`FOR UPDATE SKIP LOCKED`). The last one happens in the sweeper's transaction.

## In-process job event (not stored)

| Event | Fields | Meaning |
|---|---|---|
| `OutboxJobScheduled` | `outboxId` (UUID), `delay` (Duration) | "Run this outbox row after the delay", handled after the publishing transaction commits (R21). Holds no email or code. |

It is a Spring application event inside one app instance, so nothing leaves the process. The
RabbitMQ message and contracts/email-messaging.md were removed on 2026-11-16 (R21).

## Relationships

```text
users 1 ──── * refresh_tokens
verification_codes, registration_permissions: linked to users by email only, so nothing
about account existence is needed before verification (R7)
```

## Configuration values

| Key | Default |
|---|---|
| `tickgeek.auth.code-ttl` | 10m |
| `tickgeek.auth.max-wrong-attempts` | 5 |
| `tickgeek.auth.limits.resend-cooldown` | 60s |
| `tickgeek.auth.limits.per-email-per-hour` | 5 |
| `tickgeek.auth.limits.per-source-per-hour` | 20 |
| `tickgeek.auth.limits.global-per-hour` | 1000 |
| `tickgeek.auth.session-idle-timeout` | 30d (refresh token) |
| `tickgeek.auth.access-token-ttl` | 15m (JWT) |
| `tickgeek.email.max-attempts` | 5 |
| `tickgeek.email.retry-base-delay` | 30s (retries after 30s, 1m, 2m, 4m) |
| `tickgeek.email.sweep-interval` | 2m |
| `tickgeek.email.stuck-after` | 2m |
| `tickgeek.email.batch-size` | 20 (jobs per sweep) |
| `tickgeek.email.executor.threads` | 4 (email sender threads, R21) |
| `tickgeek.email.executor.queue-capacity` | 100 (sends waiting for a thread; beyond this the sweeper takes over) |
| `tickgeek.email.executor.shutdown-timeout` | 10s (time running sends get to finish on shutdown) |
| `tickgeek.email.retry-scheduler-threads` | 1 |

Removed: `tickgeek.email.poll-interval` (the 5 s poller is gone), and on 2026-11-16 all
`spring.rabbitmq.*` keys and the `TICKGEEK_RABBITMQ_*` environment variables (R21).
