# Research: Email Code Sign-In and Registration

Each item records a decision needed to turn [spec.md](spec.md) into a design. No
`NEEDS CLARIFICATION` items remain.

## R1. How the browser, Next.js, and the backend share a signed-in session

- **Decision**: A stateless backend that uses short-lived JWT access tokens plus a refresh token
  stored in the database (decided 2026-11-11).
  - **Access token**: a JWT signed with HS256, valid for 15 minutes. Claims: `iss=tickgeek`,
    `sub=<user id>`, `iat`, `exp`. The secret comes from the `TICKGEEK_JWT_SECRET` environment
    variable (≥ 256 bits). Each API request checks only the signature and expiry, using Spring
    Security OAuth2 Resource Server (`NimbusJwtDecoder.withSecretKey`); there is no database
    lookup and no HTTP session (`SessionCreationPolicy.STATELESS`). Tokens are issued with
    `NimbusJwtEncoder`.
  - **Refresh token**: a random 256-bit value, one per device sign-in. Its SHA-256 hash is
    stored in `refresh_tokens`. It is the "Session" entity in the spec: it gives multi-device
    sign-in, per-device sign-out (FR-013), and the 30-day timeout (R2).
  - **Transport**: the browser only talks to Next.js. Next.js keeps both tokens in HttpOnly
    cookies and sends the access token as `Authorization: Bearer`. Tokens never reach browser
    JavaScript.
- **Rationale**: Ordinary API requests need no database lookup or server-side session state,
  and refresh tokens keep sign-out and the idle timeout enforceable.
- **Accepted trade-off**: Sign-out deletes both cookies on that device immediately and revokes
  the refresh token, but a copied access token stays valid until it expires (≤ 15 minutes).
  The spec's FR-013 records this.
- **Alternatives considered**: Opaque tokens checked against the database on every request
  (the previous design; stateful); one 30-day JWT (sign-out can't be enforced); JWT plus a
  revocation list (a database check on every request, so not really stateless).
- **Not rotating refresh tokens**: rotating on every refresh, with reuse detection, protects
  better against stolen refresh tokens. But parallel requests from one browser race on the
  rotation and sign users out by mistake. The refresh token is HttpOnly and never sent to
  browser JavaScript, so V1 keeps one fixed refresh token per device. Rotation can be added
  later without changing the API.

## R2. Sliding 30-day session expiry (FR-014)

- **Decision**: Activity is recorded when the token is refreshed. `refresh_tokens.last_used_at`
  is set at sign-in and on every successful refresh. A refresh token is valid while
  `revoked_at IS NULL AND last_used_at > now() - 30 days`. An active user needs a new access
  token at least every 15 minutes, so the 30 days restart from their last signed-in request,
  give or take 15 minutes. Public pages never refresh, so they don't count as activity.
- **Who refreshes (frontend)**: `proxy.ts` handles every protected-page request. If the access
  cookie is missing or expires within 60 s and a refresh cookie exists, it calls
  `refreshAccessToken()` from `lib/api/auth.ts`. It then sets the new access cookie and
  re-sets the refresh cookie with a fresh 30-day `Max-Age`, so the browser cookie slides along
  with the database expiry. If the refresh is rejected (401), it clears both cookies and
  redirects to `/sign-in?next=<path>`.
- **Server Actions**: if a backend call returns 401 because the access token expired
  mid-action, `lib/api/client.ts` refreshes once, updates the cookies, and retries the call
  once.
- **Rationale**: Matches "only signed-in requests count" without a database write on every
  request.
- **Alternatives considered**: Fixed expiry from sign-in (contradicts the spec).

## R3. Storing and checking 6-digit codes

- **Decision**: Generate with `SecureRandom` (000000–999999, zero-padded). Store
  `HMAC-SHA256(secret, email + ":" + code)`; the secret comes from the
  `TICKGEEK_AUTH_CODE_SECRET` environment variable. Compare with a constant-time comparison.
- **Rationale**: Only 10⁶ possible codes, so a plain hash is reversible by brute force if the
  table leaks; a keyed HMAC is not. Binding the email prevents reuse of a hash across emails.
- **Alternatives considered**: BCrypt/Argon2 (slow hashing adds nothing for a 10-minute,
  5-attempt secret and slows every check); plaintext (unacceptable if the database leaks).

## R4. One live code per email and the attempt limit (FR-005–FR-007)

- **Decision**: `verification_codes.status` ∈ `PENDING` (email not yet accepted by the provider),
  `ACTIVE`, `USED`, `INVALIDATED`. A partial unique index allows only one `PENDING`/`ACTIVE` row
  per email. Requesting a code sets any previous `PENDING`/`ACTIVE` row to `INVALIDATED` and
  inserts the new `PENDING` row in the same transaction.
- **Verification is one locked transaction**: load the email's `ACTIVE` row with
  `SELECT ... FOR UPDATE`, then (a) if expired → `CODE_EXPIRED`; (b) if the HMAC matches →
  `USED`; (c) otherwise increment `attempt_count`, and on the 5th wrong attempt set
  `INVALIDATED` → `CODE_LOCKED`. Because concurrent guesses for the same email queue on the
  row lock, increments cannot be lost and no more than 5 wrong attempts are ever evaluated, and
  two tabs cannot both succeed.
- **Expiry** is derived from `expires_at`, not stored as a status (see R16 for when it is set).
- **Rationale**: The database enforces "only the newest code works", the attempt limit, and
  single use even under concurrent requests.
- **Alternatives considered**: Application-only checks (race-prone).

## R5. Rate limits (FR-008, FR-020)

- **Decision**: Count rows in `verification_codes` (every accepted request inserts one row,
  regardless of later delivery outcome). Four limits, each a separate class implementing one
  `CodeRequestLimit` interface:

  | Limit | Default | Key |
  |---|---|---|
  | Resend cooldown | 60 s since last request | email |
  | Per-email hourly | 5 per rolling hour | email |
  | Per-source hourly | 20 per rolling hour | request source |
  | System-wide hourly | 1,000 per rolling hour | none |

  All values live under `tickgeek.auth.limits.*` in configuration.
- **Concurrency** (revised 2026-11-16): Every code-request transaction takes two
  transaction-scoped advisory locks, always in this order: one for the request source, then one
  for the normalized email. Each uses the two-key form `pg_advisory_xact_lock(namespace, key)`,
  where `namespace` is a named constant per kind and `key` is the value's `String.hashCode()`
  (fixed by the Java spec, so every app instance derives the same key). Only after both locks
  does the transaction read `now`, run the four limit checks and the insert. Commit or rollback
  releases both locks.
  - Requests for the same email wait for each other, so the cooldown, the per-email cap and
    "one live code per email" hold exactly.
  - Requests from the same source wait for each other, so the per-source cap holds exactly.
    Requests without an identifiable IP share the `unknown` source (R6), so they wait for each
    other too.
  - Requests for different emails from different sources never wait for each other.
  - The fixed order (source, then email) means two code requests cannot deadlock, and
    verification (R4) takes no advisory locks. A hash collision only makes two unrelated
    requests wait; it never lets a race through.
- **The system-wide cap is approximate** (accepted 2026-11-16): the system-wide count runs
  without a lock. Requests that passed their narrow locks at the same moment can all read the
  same count before any of them commits, so the cap can be exceeded by about one request per
  code request in flight at that moment. That number is bounded by the database connection pool
  (10 per app instance by default). The cap exists to bound email cost, so a few emails over
  1,000 are harmless. Exceeding the per-email or per-source caps is not, so those stay exact.
- **Rationale**: One datastore, no new infrastructure (constitution Technology Stack); new limits
  are new classes, not new `if` branches (constitution II); only the caps that must be exact pay
  for locking, and unrelated users never queue behind each other.
- **Alternatives considered**:
  - One global lock for every code request (the design from 2026-11-11 to 2026-11-16): exact for
    every cap, but it serializes all code requests system-wide, including refused ones, so a
    flood against one email or IP delays everyone and holds database connections while waiting.
  - A third, global lock taken after the narrow checks: keeps the system-wide cap exact, but
    every accepted request still queues on it. Rejected because a slight overshoot is fine.
  - A slot table claimed with `FOR UPDATE SKIP LOCKED`: exact without waiting, but adds a table
    and startup seeding for a cap that need not be exact.
  - Per-bucket counter rows: a shared global counter row stays locked until commit, so every
    request queues on it, and fixed windows allow up to twice the cap around a window boundary.
  - Redis or Bucket4j (extra infrastructure for V1 volumes); in-memory counters (lost on restart
    and wrong with more than one instance).
- **Trade-off**: The system-wide cap can be exhausted by an attacker, blocking legitimate
  sign-ins for up to an hour. Accepted for V1 because it bounds email cost and sender
  reputation damage.

## R6. Identifying the request source

- **Decision**: The source is the client IP, established in two trusted hops:
  1. **Next.js** derives one canonical IP in `lib/auth/clientIp.ts`. It reads only the header
     that the hosting edge proxy sets and overwrites, named by the `CLIENT_IP_HEADER`
     environment variable (e.g. `x-real-ip`). A client-sent `X-Forwarded-For` is never read or
     echoed. If the header is missing or not a valid IPv4/IPv6 address, the value is
     `unknown`, which is still rate-limited as one shared source. `lib/api/client.ts` sets the
     outbound `X-Forwarded-For` to exactly that single value, replacing anything else.
  2. **Backend** trusts `X-Forwarded-For` only from the Next.js server's address (Tomcat
     `RemoteIpValve` via `server.forward-headers-strategy=native` and
     `server.tomcat.remoteip.internal-proxies`). A direct caller from any other address is
     identified by its socket address, whatever header it sends.
- **Deployment rule**: the edge proxy must overwrite `CLIENT_IP_HEADER`, never append to it. This
  is recorded in the backend docs.
- **Rationale**: Without this every request would appear to come from the Next.js server, and an
  echoed header would let clients choose their own source and bypass per-source limits.
- **Alternatives considered**: Device fingerprinting (privacy-invasive, overkill).

## R7. Hiding whether an email is registered (FR-004, SC-005)

- **Decision**: Requesting a code never looks up the user. Codes are keyed by email, not user ID,
  so the request path does identical work for every email. The account lookup happens only
  after a code verifies successfully.
- **Rationale**: Identical behaviour and timing follow from the design rather than from padding
  or artificial delays.
- **Consequence**: The V1 `verification_codes.user_id` foreign key is replaced by an `email`
  column (see [data-model.md](data-model.md)).

## R8. Registration permission (FR-019, FR-021)

- **Decision**: Verifying a code for an unknown email returns a random 256-bit registration
  token; its SHA-256 hash is stored in `registration_permissions` with
  `expires_at = code.expires_at`. Next.js keeps it in an HttpOnly cookie scoped to the browser.
  Completing registration, in one transaction: mark the permission used
  (`UPDATE ... WHERE used_at IS NULL AND expires_at > now() RETURNING email`), then
  `INSERT INTO users ... ON CONFLICT (email) DO NOTHING`, then load the user by email and issue
  a refresh token and an access token.
- **Rationale**: The code is used up at verification (FR-005) while resume still works. The
  unique email constraint plus `ON CONFLICT DO NOTHING` gives exactly one account, and the
  slower flow signs in to it (FR-021).

## R9. Sending the code email (FR-016, FR-008 failed sends)

- **Update 2026-11-13**: the *trigger* is superseded by R18–R20. The 5-second scheduled poller is
  replaced by a RabbitMQ message published after the request commits, retries move to RabbitMQ
  delay queues, and a slow sweeper re-publishes stuck jobs. Steps 1–4 below (check the code is
  still `PENDING`, send, activate on success, give up after 5 attempts) are unchanged and now run
  in the message consumer. The "message broker (out of scope for V1)" alternative was
  reconsidered once the constitution (v1.1.0) added RabbitMQ.
- **Update 2026-11-16**: RabbitMQ is removed again (R21). Steps 1–4 now run on an in-process
  email thread pool, started by a Spring event after the request commits; retries use an
  in-process scheduler, and the sweeper (R20) stays as the recovery path.

- **Decision**: The code request writes an `outboxes` row in the same transaction, linked to its
  code by `outboxes.verification_code_id`. A scheduled worker claims due rows with
  `SELECT ... FOR UPDATE SKIP LOCKED` and, for each one:
  1. Checks that the linked code is still `PENDING`. If it was replaced (`INVALIDATED`), the job
     is set to `CANCELLED` and its payload erased, and nothing is sent.
  2. Sends via the Brevo transactional email HTTP API
     (`POST https://api.brevo.com/v3/smtp/email`, `api-key` header from `BREVO_API_KEY`).
  3. On acceptance, in one transaction: sets the job to `SENT` with the payload erased, and
     activates the code with
     `UPDATE ... SET status='ACTIVE', expires_at=now()+ttl WHERE id=? AND status='PENDING'` (R16).
  4. On failure: retries with exponential backoff up to 5 attempts. After the last failure the
     job becomes `ERROR`, the payload is erased, and the code is set to `INVALIDATED`. The user
     never received it, and resend is available after the normal cooldown.
- **Remaining race**: if a resend lands between step 1 and step 3, the old email is delivered
  but the step-3 activation matches no row, so that code cannot be used. The user sees
  `CODE_EXPIRED` and uses the newer email. This is safe and rare, so it's accepted rather than
  holding a database lock across the HTTP call.
- **Rationale**: The request returns the same "code sent" response no matter what happens with
  delivery (the user cannot be told synchronously anyway). The outbox already exists in the V1
  schema. Removing the code after use limits exposure of the plaintext code at rest.
- **Alternatives considered**: Sending inside the request (slow, and leaks delivery failures as
  timing differences); a message broker (out of scope for V1); deciding only by expiry, which
  would send codes that had already been replaced or used.
- **Testing**: `EmailSender` is an interface; integration tests use a recording fake, and one
  test checks the Brevo client against a mock HTTP server.

## R10. Security events (removed 2026-11-17)

- **Decision**: Removed. No security events are recorded; the `security_events` table,
  `SecurityEventRecorder` and FR-023 are gone.

## R11. Email normalization and validation (FR-002)

- **Decision**: Trim, lowercase with `Locale.ROOT`, validate with Bean Validation `@Email` and
  max length 254. Store and compare only the normalized form; `users.email` stays `UNIQUE`.
- **Rationale**: Simple, predictable, and enough for "compare case-insensitively".
- **Alternatives considered**: `citext` extension (extra extension for no added benefit once
  values are normalized on write).

## R12. Return to the original page (FR-022)

- **Decision**: The Next.js route guard (`proxy.ts`) redirects signed-out users to
  `/sign-in?next=<path>`. `next` is accepted only if it starts with `/` and not `//` or `/\`;
  otherwise the user goes to `/dashboard`.
- **Rationale**: Prevents open redirects without keeping a list of allowed URLs.

## R13. Frontend structure (constitution IV)

- **Decision**: App Router pages are Server Components. Only the forms (which use
  `useActionState`) and the resend countdown are `"use client"`. Server Actions in `action/` call
  typed functions in `lib/api/auth.ts`; nothing else calls the backend.
- **Rationale**: Directly satisfies the constitution's frontend rules.

## R14. Testing and coverage (constitution V)

- **Decision**: JUnit 5 + Mockito for service unit tests; `@SpringBootTest` + MockMvc +
  Testcontainers PostgreSQL for one or more integration tests per endpoint; JaCoCo enforces 80%
  line coverage in `mvn verify`.
- **Required integration tests for risky boundaries** (feature is not complete without them):
  - Concurrency (parallel threads against real PostgreSQL): two code requests for one email;
    N parallel requests for *different* emails from one source never exceed the per-source cap;
    N parallel requests across sources exceed the system-wide cap by at most the number of
    parallel threads (R5); while one transaction holds the locks for one email and source, a
    request for another email from another source completes and a request for the held email
    waits; parallel wrong guesses
    on one code evaluate at most 5 wrong attempts; two verifications of one correct code
    produce one success; two registrations for one email create one account and both sign in.
  - Outbox: a job whose code was replaced before sending is cancelled, not sent, and its
    payload erased; a job sent after replacement does not activate its code; a job that
    exhausts retries invalidates its code.
  - Email dispatch (in-process, R21; replaces the RabbitMQ messaging tests of R18–R20): a
    committed code request is sent without any scheduled job running; a rolled-back request
    dispatches nothing; a failed send is retried by the retry scheduler and succeeds; the same
    job dispatched twice sends one email; a job that was never dispatched (row inserted with no
    event, `next_attempt_at` older than stuck-after) is sent by the sweeper; a job rejected by a
    full email executor stays `PENDING` and is sent by the sweeper.
  - Source identification: a request from an untrusted address carrying a spoofed
    `X-Forwarded-For` is counted under its real socket address; a request from the trusted
    proxy address is counted under the forwarded IP.
  - Security matcher: `/api/auth/me` returns 401 without a token, and with an expired,
    tampered, or wrong-issuer JWT; `/sign-out` returns 204 with a missing, revoked, or valid
    refresh token and revokes only that one.
  - Tokens: a refresh token unused for 30 days, or revoked by sign-out, is rejected by
    `/token-refreshes`; a valid one returns a new access token and moves `last_used_at`; an
    access token keeps working after sign-out until it expires (documents the accepted
    trade-off); two parallel refreshes with the same token both succeed.
  - Migration: removed 2026-11-16. V2 was merged into V1 (R15 update), so there are no legacy
    rows to normalize; `ApplicationContextIT` checks that V1 applies and the JPA mappings
    validate.
- **Scope note**: The 80% gate is applied to the backend. Frontend checks are
  `npm run lint` + `npm run build` + the manual flow in [quickstart.md](quickstart.md); adding a
  frontend test runner is deferred until there is frontend logic worth unit testing (YAGNI).

## R15. Existing V1 schema

- **Update 2026-11-13**: superseded. No database with the old schema is kept: every database is
  rebuilt from scratch. So V2 was merged into `V1__create_ticket_system.sql`, which now creates
  the final tables directly, and the collision check, normalize and backfill steps below are
  gone. The text below is kept as history.

- **Decision**: Add a `V2__email_code_auth.sql` migration rather than editing V1: drop
  `users.password`, add `email_verified_at` and `created_at`, replace `verification_codes`, add
  `registration_permissions`, `refresh_tokens`, and alter `outboxes` (nullable
  `payload`, `verification_code_id`, `CANCELLED` status).
- **Existing users** (V1 never enforced normalized emails):
  1. **Collision check first**: if any two rows share `lower(btrim(email))`, the migration
     raises an exception listing the colliding normalized emails and makes no changes. An
     operator merges or removes the duplicates by hand, then re-runs. Silently picking a winner
     could hand one person's account to another.
  2. **Normalize**: `UPDATE users SET email = lower(btrim(email))`, then add
     `CHECK (email = lower(btrim(email)))` so un-normalized emails can never be stored again.
  3. **Backfill**: `created_at` is set to the migration time for existing rows.
     `email_verified_at` is nullable. Legacy rows stay `NULL` (unverified), because V1
     registration never verified an email. A legacy user's first successful code verification
     sets it, which proves ownership. Every account created by the new flow is verified at
     creation.
- **Rationale**: V1 may already have been applied to developer databases; editing it would break
  Flyway checksums. The empty staged `V2__user_pending_registration.sql` is replaced by this file.

## R16. When the 10-minute code lifetime starts (FR-005)

- **Decision**: The lifetime starts when the email provider accepts the email, not when the
  request is made. Codes are created `PENDING` with `expires_at` NULL. The email sender (R21)
  activates them and sets `expires_at = accepted_at + 10 min` (R9). A `PENDING` code cannot be
  verified; it returns `CODE_EXPIRED`, which in practice cannot happen because the user doesn't
  have it yet.
- **API consequence**: `POST /code-requests` no longer returns `expiresAt`, because it is not
  known yet. The UI states "The code is valid for 10 minutes", using the configured value. The
  registration permission still expires at the verified code's `expires_at`.
- **Rationale**: Matches the spec's "10 minutes after it is sent" even when delivery is retried,
  so a delayed email still gives the user the full 10 minutes.
- **Alternatives considered**: TTL from request acceptance (simpler, but a retried email could
  arrive nearly or already expired).

## R17. Which endpoints are public (Spring Security)

- **Decision**: Explicit matchers, nothing wider:
  - `permitAll`: `POST /api/auth/code-requests`, `POST /api/auth/code-verifications`,
    `POST /api/auth/registrations`, `POST /api/auth/token-refreshes`,
    `POST /api/auth/sign-out`. These five endpoints are authenticated by what they carry in
    the body (a code, a registration token, or a refresh token), not by a JWT.
  - `authenticated` (a valid JWT is required): everything else under `/api/**`, including
    `GET /api/auth/me`.
  - `denyAll`: any other path.
- **Sign-out**: Public so it can be idempotent. `RefreshTokenService.revoke(refreshToken)`
  revokes the token if it is valid and does nothing otherwise. It always returns 204 and never
  reveals whether the token was valid.
- **Rationale**: A pattern like `/api/auth/*` would make `/me` public by accident. Listing the
  five public endpoints by method and path makes any new endpoint protected by default.

## R18. Triggering the send with a RabbitMQ message (replaces the 5 s poller)

- **Superseded 2026-11-16 by R21**: RabbitMQ is removed. The outbox row, the after-commit event
  and the idempotent processor described here are kept; the broker publish and
  `CodeEmailListener` are replaced by an in-process `@Async` dispatcher. The text below is kept
  as history.

- **Decision** (decided 2026-11-13): the outbox row stays the source of truth; RabbitMQ only
  says "this job is ready now".
  1. `CodeRequestService` hands the job to `CodeEmailQueue.enqueue(...)`, which saves the
     `outboxes` row in the request transaction and publishes a Spring application event
     `OutboxJobScheduled(outboxId, delay = 0)`.
  2. `OutboxMessagePublisher` listens with `@TransactionalEventListener(phase = AFTER_COMMIT)`
     and sends `{"outboxId": "<uuid>"}` to RabbitMQ (persistent message, durable queue; names in
     [contracts/email-messaging.md](contracts/email-messaging.md)). A rolled-back request never
     publishes. If the publish fails (`AmqpException`), it is logged and swallowed: the request
     already committed and has returned "code sent"; the sweeper (R20) recovers the job.
  3. `CodeEmailListener` (`@RabbitListener`) receives the message and calls
     `OutboxJobProcessor.process(outboxId)` in one transaction. The processor loads the row
     with `SELECT ... WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= now
     FOR UPDATE SKIP LOCKED`. No row → the message is a duplicate, stale, or another consumer
     holds the job: it is acknowledged and dropped. Otherwise steps 1–4 of R9 run as before.
- **The message carries only the outbox id**, never the email or the code. The plaintext code
  stays in the one place R9 already allows (the unsent payload, erased after sending), and is
  never copied into the broker, its disk, or its management UI.
- **Delivery is at-least-once**, so the consumer is idempotent: the status and
  `next_attempt_at` check plus the row lock mean duplicates, redeliveries, and sweeper
  re-publishes never send a second email.
- **Publish latency**: the after-commit publish runs on the request thread. A broker that is
  down would add the connection timeout to the response, so `spring.rabbitmq.connection-timeout`
  is set to 2 s. Normal publishes take a few milliseconds.
- **Rationale**: Emails go out immediately instead of up to 5 s later, with no polling in the
  normal path. Keeping the outbox avoids the dual-write problem: committing the code and
  publishing a message cannot be made atomic, and a publish-only design would lose emails
  whenever the broker is unreachable at commit time.
- **Alternatives considered**: publish inside the transaction (a message for a code that then
  rolls back); publish only, no outbox table (emails lost when the broker is down); publisher
  confirms with a synchronous wait (adds latency and still needs a recovery path, which the
  sweeper already is); an `@Async` publisher (hides latency but adds an executor for a few-ms
  call; not needed at V1 volume).

## R19. Retrying failed sends with RabbitMQ delay queues

- **Superseded 2026-11-16 by R21**: the backoff formula and the 5-attempt limit are kept; the
  delay queues and the dead-letter queue are replaced by an in-process retry scheduler. The text
  below is kept as history.

- **Decision**: no broker plugin. On `EmailDeliveryException` the processor records the failure
  as today (`attempts + 1`, `next_attempt_at = now + backoff`, where backoff is
  `retry-base-delay × 2^(attempts before this failure)`), and publishes
  `OutboxJobScheduled(outboxId, delay = backoff)`. After commit, the publisher sends the message
  to the delay queue for that delay. Each delay queue has `x-message-ttl` = its delay and
  dead-letters expired messages back to the main exchange, so the message reappears in the main
  queue when the retry is due.
  - With the defaults (`retry-base-delay` 30 s, `max-attempts` 5) there are four delay queues:
    30 s, 1 min, 2 min, 4 min. They are declared at startup from configuration, and each queue
    name includes its delay in milliseconds. Changing the configuration therefore creates new
    queues rather than failing on a TTL mismatch with an existing one.
  - The 5th failure sets the job to `ERROR` and the code to `INVALIDATED` (R9 step 4) and
    publishes nothing more.
- **Dead-letter queue**: the main queue dead-letters rejected messages to
  `tickgeek.email.code.dead`, and the listener is configured with
  `default-requeue-rejected: false`. A message lands there only when it cannot be processed at
  all (unreadable body, or an unexpected exception such as the database being down). Business
  failures never go there; their outcome is the job's `ERROR` status in PostgreSQL. A job whose
  message was dead-lettered by an outage is still `PENDING`, so the sweeper re-publishes it.
- **Rationale**: Backoff happens in the broker without holding a consumer thread or polling.
  Fixed per-delay queues avoid the head-of-line blocking of per-message TTLs, in which a short
  delay waits behind a long one in the same queue.
- **Alternatives considered**: the `rabbitmq_delayed_message_exchange` plugin (needs a custom
  broker image); Spring Retry inside the listener (blocks a consumer thread for minutes); the
  sweeper handling retries (poll-driven again).

## R20. Safety sweeper for jobs whose message was lost

- **Update 2026-11-16 (R21)**: kept unchanged, with the same query, interval and threshold. With
  RabbitMQ gone, "message was lost" now means the in-memory task was lost: the app stopped
  before sending, the email executor was full, or a scheduled retry was dropped by a restart.
  Its events go to the in-process dispatcher instead of the broker.

- **Decision**: `OutboxSweeper` runs every `tickgeek.email.sweep-interval` (2 min). In one
  statement it claims jobs that are overdue and refreshes their due time:

  ```sql
  UPDATE outboxes SET next_attempt_at = :now
  WHERE id IN (SELECT id FROM outboxes
               WHERE status = 'PENDING' AND next_attempt_at < :now - :stuckAfter
               ORDER BY created_at LIMIT :batchSize
               FOR UPDATE SKIP LOCKED)
  RETURNING id
  ```

  Then, after commit, it publishes one `OutboxJobScheduled(id, 0)` per returned id.
  `stuck-after` (2 min) is the grace period after a job's expected delivery time.
- **`next_attempt_at` now means "when the message for this job is due"**: the request time for
  a new job, `now + backoff` for a retry. A healthy job is processed at about that time, so only
  jobs whose message never arrived pass the `stuck-after` threshold. Resetting
  `next_attempt_at` stops the next sweep from re-publishing the same job while its new message is
  in flight. `SKIP LOCKED` keeps two app instances from sweeping the same rows and skips a job
  that a consumer is sending right now.
- **Worst case**: with the broker down at request time, the email is sent within about
  `stuck-after + sweep-interval` (≈ 4 min) of the broker coming back. SC-002 (95% within
  1 minute) is met because the normal path is immediate.
- **Rationale**: The only recovery path for a lost publish or an outage-time dead-letter, without
  publisher confirms or polling in the normal path.
- **Index**: a partial index `outboxes (next_attempt_at) WHERE status = 'PENDING'` keeps the
  sweep cheap as finished jobs pile up between cleanups.

## R21. Sending email in-process: after-commit event + `@Async` pool (replaces RabbitMQ)

- **Decision** (decided 2026-11-16): RabbitMQ is removed from the project. The outbox row stays
  the source of truth, and Spring application events plus a bounded thread pool replace the
  broker.
  1. Unchanged: `CodeEmailQueue.enqueue(...)` saves the `outboxes` row in the request
     transaction and publishes `OutboxJobScheduled(outboxId, delay = 0)`.
  2. `OutboxJobDispatcher` replaces `OutboxMessagePublisher` and `CodeEmailListener`. Its one
     listener method is `@TransactionalEventListener(phase = AFTER_COMMIT)` and
     `@Async(EMAIL_EXECUTOR)`: after commit, Spring hands the event to the email executor and
     the request thread returns straight away. A rolled-back request never dispatches. On the
     executor thread:
     - delay zero → `OutboxJobProcessor.process(outboxId)`;
     - delay above zero (a retry) → the email retry scheduler runs
       `OutboxJobProcessor.process(outboxId)` at `now + delay`.
  3. `OutboxJobProcessor` is unchanged: the same claim
     (`status = 'PENDING' AND next_attempt_at <= now FOR UPDATE SKIP LOCKED`), R9 steps 1–4,
     and on a failed send with attempts left it publishes `OutboxJobScheduled(id, backoff)`,
     which reaches the dispatcher after that transaction commits.
- **Email executor**: a dedicated `ThreadPoolTaskExecutor` bean, not Boot's shared
  `applicationTaskExecutor`. It has `tickgeek.email.executor.threads` threads (4) and a queue of
  `tickgeek.email.executor.queue-capacity` (100).
  - **When it is full** (decided 2026-11-16): the rejection handler logs a warning with the
    outbox id and discards the task. No exception reaches the request thread, whose transaction
    has already committed. The job stays `PENDING` and the sweeper sends it (R20).
  - **On shutdown**: running sends get up to `tickgeek.email.executor.shutdown-timeout` (10 s)
    to finish; queued tasks are dropped and recovered by the sweeper after restart.
- **Retry scheduler** (decided 2026-11-16): a dedicated `ThreadPoolTaskScheduler` bean with
  `tickgeek.email.retry-scheduler-threads` threads (1). It is created whether or not
  `tickgeek.scheduling.enabled` is set, because it is part of sending, not a background job.
  Retry timing is the same as R19: backoff = `retry-base-delay × 2^(attempts before this
  failure)`, so 30 s, 1 min, 2 min, 4 min with the defaults; the 5th failure sets the job to
  `ERROR` and the code to `INVALIDATED`. A scheduled retry is held only in memory. If the app
  restarts first, the job is still `PENDING` with `next_attempt_at` in the past, and the sweeper
  sends it once it is overdue by `stuck-after`.
- **Idempotency** is unchanged: the row lock plus the status and due-time check mean a task from
  the sweeper and a late in-memory task for the same job send one email.
- **Several app instances**: each instance sends the jobs it created. The sweepers on all
  instances share overdue jobs through `SKIP LOCKED`, as before.
- **Worst case**: if the app stops after the request commits and before the send, the email is
  sent within about `stuck-after + sweep-interval` (≈ 4 min) of an instance running again. The
  normal path is immediate, so SC-002 (95% within 1 minute) holds.
- **Rationale**: Removes a whole service from development, testing and deployment without losing
  any guarantee. Durability came from the outbox row and the sweeper, not from the broker, and
  both stay. Retry timing and the outbox column meanings stay the same, and the after-commit
  event still means a rolled-back request never sends.
- **Alternatives considered**:
  - Keep RabbitMQ (R18–R20): another service to run for no extra guarantee at V1 volume.
  - Let the sweeper drive retries: poll-driven, retries up to one sweep late, and
    `next_attempt_at` / `stuck-after` would need new meanings.
  - `CallerRunsPolicy` when the executor is full: the request thread would call Brevo, so
    response time would depend on email load, a timing signal FR-004 rules out.
  - Boot's default `applicationTaskExecutor`: shared with any other async work, and its queue is
    unbounded by default, so a backlog grows in memory instead of falling back to the sweeper.
  - Retrying with `Thread.sleep` or Spring Retry on the executor thread: holds a sender thread
    for minutes.
- **Testing**: R14 "Email dispatch". Integration tests keep the short `retry-base-delay` and
  advance the `MutableClock` past each backoff, as the RabbitMQ tests did. No Testcontainer
  besides PostgreSQL is needed.
