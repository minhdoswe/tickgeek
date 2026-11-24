# Research: Email Code Sign-In: Request a Code

Each item records a decision behind the code-request slice described in [spec.md](spec.md).
Decision IDs keep their original numbers, so there are gaps. No `NEEDS CLARIFICATION` items
remain.

## R3. Storing 6-digit codes

- **Decision**: Generate with `SecureRandom` (000000–999999, zero-padded). Store only
  `HMAC-SHA256(secret, email + ":" + code)` as hex; the secret comes from the
  `TICKGEEK_AUTH_CODE_SECRET` environment variable and must be at least 32 bytes, or the app
  refuses to start.
- **Rationale**: Only 10⁶ possible codes, so a plain hash is reversible by brute force if the
  table leaks; a keyed HMAC is not. Binding the email prevents reuse of a hash across emails.
- **Alternatives considered**: BCrypt/Argon2 (slow hashing adds nothing for a short-lived
  6-digit secret); plaintext (unacceptable if the database leaks).

## R4. One live code per email (FR-006)

- **Decision**: `verification_codes.status` ∈ `PENDING`, `ACTIVE`, `USED`, `INVALIDATED`. A
  partial unique index allows only one `PENDING`/`ACTIVE` row per email. Requesting a code sets
  any previous `PENDING`/`ACTIVE` row to `INVALIDATED` and inserts the new `PENDING` row in the
  same transaction.
- **Rationale**: The database enforces "only the newest code is live" even under concurrent
  requests.
- **Alternatives considered**: Application-only checks (race-prone).

## R5. Rate limits (FR-008, FR-020)

- **Decision**: Count rows in `verification_codes` (every accepted request inserts one row).
  Four limits, each a separate class implementing one `CodeRequestLimit` interface:

  | Limit | Default | Key | Refusal |
  |---|---|---|---|
  | Resend cooldown | 60 s since last request | email | states the seconds to wait, `Retry-After` set |
  | Per-email hourly | 5 per rolling hour | email | generic, no `Retry-After` |
  | Per-source hourly | 20 per rolling hour | request source | generic, no `Retry-After` |
  | System-wide hourly | 1,000 per rolling hour | none | generic, no `Retry-After` |

  All values live under `tickgeek.auth.limits.*` in configuration. The cooldown runs first.
- **Concurrency** (revised 2026-11-19: the per-source lock was removed): every code-request
  transaction takes one transaction-scoped advisory lock for the normalized email,
  `pg_advisory_xact_lock(EMAIL_LOCKS, email.hashCode())`. Only after the lock does it read
  `now`, run the four limit checks and insert. Commit or rollback releases the lock.
  - Requests for the same email wait for each other, so the cooldown, the per-email cap and
    "one live code per email" hold exactly.
  - Requests for different emails never wait for each other, whatever their source.
  - A hash collision only makes two unrelated requests wait; it never lets a race through.
  - (added 2026-11-20) Every app connection sets `lock_timeout=2s` and
    `idle_in_transaction_session_timeout=30s` (Hikari `data-source-properties.options` in
    `application.yaml`). A request still waiting for its email's lock after 2 s is refused with
    the generic 429; `CodeRequestLockRepository` turns SQLSTATE 55P03 into
    `CannotAcquireLockException` and `CodeRequestService` maps it. Without this, one stuck
    transaction would hold the lock until TCP keepalive notices (up to ~2 h), and retries for
    that email would wait while holding pooled connections until the pool of 10 was empty.
    No `statement_timeout`, so Flyway index builds are never cut off.
- **The per-source and system-wide caps are approximate**: their counts run without a lock.
  Requests for different emails that run at the same moment can all read the same count before
  any of them commits, so a cap can be exceeded by about the number of code requests in flight
  at that moment. That number is bounded by the database connection pool (10 per app instance
  by default). Both caps exist to bound abuse and email cost, so a few requests over is
  accepted. The cooldown and per-email cap protect a single inbox, so they stay exact.
- **Rationale**: One datastore, no new infrastructure; new limits are new classes, not new `if`
  branches; only the caps that must be exact pay for locking, and unrelated users never queue
  behind each other.
- **Alternatives considered**:
  - A per-source lock before the email lock (the design until 2026-11-19): makes the per-source
    cap exact, but every request from one IP queues behind the others. Removed 2026-11-19.
  - One global lock for every code request: exact for every cap, but serializes all code
    requests system-wide, so a flood against one email or IP delays everyone.
  - Redis or Bucket4j (extra infrastructure at V1 volumes); in-memory counters (lost on restart
    and wrong with more than one instance).
- **Trade-off**: The system-wide cap can be exhausted by an attacker, blocking legitimate code
  requests for up to an hour. Accepted for V1 because it bounds email cost.

## R6. Identifying the request source

- **Decision**: The source is the client IP, established in two trusted hops:
  1. **Next.js** derives one IP in `lib/auth/clientIp.ts`. It reads only the header that the
     hosting edge proxy sets and overwrites, named by the `CLIENT_IP_HEADER` environment variable
     (e.g. `x-real-ip`). A client-sent `X-Forwarded-For` is never read. If the header is missing
     or not a valid IPv4/IPv6 address, the value is `unknown`, which is still rate-limited as one
     shared source. `lib/api/client.ts` sends that value as `X-Tickgeek-Client-Ip`, together with
     `X-Tickgeek-Frontend-Secret` (`FRONTEND_SECRET`).
  2. **Backend** (`RequestSourceResolver`) uses `X-Tickgeek-Client-Ip` only when
     `X-Tickgeek-Frontend-Secret` equals `tickgeek.frontend.secret` (constant-time compare, at
     least 32 bytes, checked at startup). A value that is not an IP literal is recorded as
     `unknown`. Any other caller is identified by its address as Tomcat resolves it:
     `RemoteIpValve` (`server.forward-headers-strategy=native`) reads `X-Forwarded-For` only from
     `TICKGEEK_TRUSTED_PROXIES` (on Render, its internal proxy). A blank address is `unknown`.
- **Deployment rule**: the edge proxy must overwrite `CLIENT_IP_HEADER`, never append to it
  (`x-forwarded-for` on Vercel). Recorded in the root README, "Deploy".
- **Rationale**: Without this every request would appear to come from the Next.js server, and an
  echoed header would let clients choose their own source and bypass per-source limits.
- **Changed 2026-11-20**: the backend used to trust `X-Forwarded-For` by the Next.js server's
  address. On Vercel + Render that fails: Vercel calls out from changing, shared addresses that
  can't be listed, and Render's proxy appends to a caller's `X-Forwarded-For` instead of
  replacing it. A shared secret proves the caller is the frontend wherever it runs.
- **Alternatives considered**: Device fingerprinting (privacy-invasive, overkill); keeping
  address trust and accepting that all users share the frontend's IPs (per-source limit
  becomes meaningless).

## R7. Hiding whether an email is registered (FR-004, SC-005)

- **Decision**: Requesting a code never looks up a user. Codes are keyed by email, not user ID,
  so the request path does identical work for every email.
- **Rationale**: Identical behaviour and timing follow from the design rather than from padding
  or artificial delays.

## R9. Queuing the code email

- **Decision**: The code request saves an `outboxes` row in the same transaction, and the code
  points to it by `verification_codes.outbox_id`, one outbox per code (unique). The outbox is
  saved first and `CodeEmailQueue.enqueue` returns its id. Its payload is JSON `{"to": …, "code": …}`, and it
  starts `PENDING` with `next_attempt_at` = the request time. `CodeEmailQueue` then publishes a
  Spring application event `OutboxJobScheduled(outboxId, delay = 0)`. The event carries only the
  outbox id, never the email or the code. Nothing listens for the event yet.
- **Outbox id** (added 2026-11-20): a UUIDv7 that Hibernate generates on persist
  (`@UuidGenerator(style = VERSION_7)`). Time-ordered ids go to the end of the primary key index
  instead of random spots, and because the id is unset before saving, `save()` inserts directly
  instead of first selecting by id (the previous app-set UUIDv4 cost one extra SELECT per code
  request).
- **Rationale**: The code and its email job commit or roll back together, so a code never exists
  without its email job, and the request returns without waiting for an email provider. The
  plaintext code is kept only in the unsent payload.
- **Link direction** (revised 2026-11-24): a code always needs an outbox, but an outbox doesn't
  always belong to a code, so the code holds the link and the email module knows nothing about
  codes. The foreign key has no `ON DELETE` action: an outbox can't be deleted while a code
  points to it, so a future outbox cleanup must delete the codes first.
- **Previous decision** (until 2026-11-24): `outboxes.verification_code_id` linked each outbox to
  its code (one code, many outboxes allowed).
- **Alternatives considered**: Sending inside the request (slow, and leaks delivery failures as
  timing differences).

## R11. Email normalization and validation (FR-002)

- **Decision**: Strip surrounding whitespace and lowercase with `Locale.ROOT` before
  validating, then validate with Bean Validation `@NotBlank`, `@Email` and max length 254. Store
  and compare only the normalized form. The browser checks for an empty or malformed email
  first; the backend check is the source of truth.
- **Rationale**: Simple, predictable, and enough for "compare case-insensitively".
- **Alternatives considered**: `citext` extension (extra extension for no added benefit once
  values are normalized on write).

## R13. Frontend structure

- **Decision**: App Router pages are Server Components. Only `EmailForm` (which uses
  `useActionState`) is `"use client"`. The Server Action in `action/authAction.ts` calls the
  typed function in `lib/api/auth.ts`; nothing else calls the backend.
- **Rationale**: Directly satisfies the constitution's frontend rules.

## R14. Testing and coverage

- **Decision**: JUnit 5 + Mockito for unit tests; `@SpringBootTest` + MockMvc + Testcontainers
  PostgreSQL for integration tests; JaCoCo enforces 80% line coverage in `mvn verify`.
- **Required integration tests for risky boundaries**:
  - Concurrency (parallel threads against real PostgreSQL): two code requests for one email
    leave one live code; while one transaction holds the lock for one email, a request for
    another email completes and a request for the held email waits; N parallel requests for
    different emails from one source exceed the per-source cap, and N parallel requests across
    sources exceed the system-wide cap, by at most the number of parallel threads.
  - Source identification: a request from an untrusted address carrying a spoofed
    `X-Forwarded-For` is counted under its real socket address; a request from the trusted proxy
    address is counted under the forwarded IP.
  - Security matcher: the code request endpoint works without a token; every other path is
    denied.
  - Schema: `ApplicationContextIT` checks that V1 applies and the JPA mappings validate.
- **Scope note**: The 80% gate is applied to the backend. Frontend checks are `npm run lint` +
  `npm run build` + the manual flow in [quickstart.md](quickstart.md).

## R15. Migrations

- **Decision** (revised 2026-11-20): `V1__create_ticket_system.sql` creates the schema. Later
  schema changes go in new migrations and V1 is never edited again.
  `V2__index_outboxes_verification_code_id.sql` adds a partial index on
  `outboxes.verification_code_id`. Postgres does not index foreign key columns, so without it
  every delete from `verification_codes` scanned all of `outboxes`: deleting 200 codes took 45.7 s
  against 2M outboxes, and 11.6 ms with the index. `ApplicationContextIT` fails if any foreign
  key column is left without an index.
- **Changes on a live database** (added 2026-11-24): production is live, and during a deploy the
  old backend keeps serving while the new one runs its migration. So a migration must work with
  both the backend before it and the one after it: add first, remove in a later release
  (expand, then contract). Reversing the code ↔ outbox link (R9) takes two releases:
  - Release 1, `V3__add_verification_codes_outbox_id.sql`: adds `verification_codes.outbox_id`
    (nullable, FK, unique index), copies the existing links from
    `outboxes.verification_code_id`, and keeps the old column. The new backend writes only
    `outbox_id` and no longer maps `verification_code_id`. `OutboxLinkMigrationIT` checks the copy
    and that the old backend's writes still work after V3.
  - Release 2, `V4__require_verification_codes_outbox_id.sql` (deploy only once release 1 is
    fully live): copies again to catch codes the old backend created during the release 1
    deploy, sets `outbox_id` NOT NULL, and drops `outboxes.verification_code_id` with its V2
    index. `VerificationCode.outboxId` is mapped `nullable = false`. `OutboxLinkMigrationIT`
    checks the late copy, that a code without an outbox is refused, that the old column and
    index are gone, and that the release 1 backend's writes still work after V4. Before
    deploying, this must return 0 on production, or `SET NOT NULL` fails and the deploy rolls
    back: `SELECT count(*) FROM verification_codes vc WHERE vc.outbox_id IS NULL AND NOT EXISTS
    (SELECT 1 FROM outboxes o WHERE o.verification_code_id = vc.id)`.
- **Previous decision** (until 2026-11-20): one migration, V1 only, edited in place, because no
  database with an older schema was kept.

## R16. When the code lifetime starts

- **Decision**: Codes are created `PENDING` with `expires_at` NULL. The 10-minute lifetime is
  meant to start when the email provider accepts the email, so `POST /code-requests` returns
  `codeValidMinutes` (from `tickgeek.auth.code-ttl`) and no `expiresAt`.
- **Rationale**: A delayed email still gives the user the full 10 minutes.

## R17. Which endpoints are public (Spring Security)

- **Decision**: Explicit matchers, nothing wider:
  - `permitAll`: `POST /api/auth/code-requests` only.
  - `denyAll`: every other path and method.
  - Stateless sessions, CSRF disabled (no cookies reach the backend). Error dispatches are
    permitted so error responses render.
- **Rationale**: Listing public endpoints by method and path makes any new endpoint closed by
  default.
