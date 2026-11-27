# Research: Email Code Sign-In: Send the Code Email

Design decisions for [spec.md](spec.md). Decisions marked **(user)** were chosen by the user on
2026-11-26; the spec deviations they cause are listed in [plan.md](plan.md) under
"Spec deviations". Rule files named here are in
`.claude/skills/supabase-postgres-best-practices/references/`.

## R1. Starting a job: in-process event and TaskScheduler

- **Decision**: `CodeEmailQueue` keeps publishing `OutboxJobScheduled(outboxId, delay)` inside the
  request transaction (001 R9). `EmailJobListener` receives it with
  `@TransactionalEventListener(phase = AFTER_COMMIT)`, so a job never starts for a rolled-back
  request, and hands it to Spring's `TaskScheduler` to run at `clock.instant() + delay`. The
  first attempt has delay 0. A retry is announced the same way, from inside the transaction that
  records the failure, with delay = the backoff (R6).
- **Rationale**: The first attempt starts right after the request commits (FR-001, SC-001). No
  new infrastructure is needed.
- **Known gap (user)**: The schedule lives in memory. A restart loses every waiting retry, and two
  running copies are not kept apart, so FR-009 and the restart edge case are **not** met yet. A
  sweeper that picks up due `PENDING` rows will be added in a later feature. It should claim rows
  with `FOR UPDATE SKIP LOCKED` (`lock-skip-locked.md`), and `outboxes` will need an index on
  `next_attempt_at` for `status = 'PENDING'` at that point.
- **To verify**: Spring Boot 4.1 creates a `TaskScheduler` bean when `@EnableScheduling` is
  present (`SchedulingConfig`). This is from memory, not checked. The context test
  (`ApplicationContextIT`) confirms it.
- **Alternatives considered**: RabbitMQ delayed messages (on the approved stack, durable, but a
  new broker to run, deferred with the sweeper); `@Scheduled` polling only (adds up to one poll
  interval to every first attempt).

## R2. One attempt in three steps

- **Decision**: An attempt runs as:
  1. **TX 1** `OutboxJobSteps.claim`: load the outbox, run the checks (R3), increment `attempts`,
     commit, return the payload.
  2. **No transaction**: call `EmailSender`.
  3. **TX 2** `OutboxJobSteps.recordSuccess` or `recordFailure`: reload the outbox, write the
     result and its effect on the code, commit.

  `OutboxJobSteps` is a separate Spring bean with `@Transactional` methods **(user)**, so the
  calls from `EmailJobProcessor` go through the transaction proxy.
- **Rationale**:
  - The attempt counter is committed before the provider call, so a crash during the send still
    counts it and a job that keeps crashing still reaches the limit.
  - No database connection or row lock is held during a slow provider call
    (`lock-short-transactions.md`). `idle_in_transaction_session_timeout=30s` in the datasource
    options would also kill a transaction left open that long.
  - TX 2 reloads its rows, so its checks see changes made during the send.
- **Alternatives considered**: One transaction around everything (loses the count on a crash,
  holds a connection during the HTTP call); `TransactionTemplate` inline in the processor (makes
  the 30-line method limit harder to keep).

## R3. Checks before an attempt, and "only if PENDING"

- **Decision**: `claim` checks in this order and stops at the first match:
  1. outbox not `PENDING` → nothing (FR-010);
  2. the owner says the job is no longer wanted (code not `PENDING`) → cancel the outbox (R5);
  3. `next_attempt_at > now` → nothing; its own event comes later;
  4. `attempts >= max-attempts` → abandon with `"max attempts reached"` (reached only when an
     earlier attempt crashed between TX 1 and TX 2);
  5. otherwise claim.

  Every state change applies only while its row is `PENDING`, and does nothing otherwise
  ([data-model.md](data-model.md)).
- **Rationale**: Finished jobs stop first. Each change is safe if it runs twice.
- **Code-row guard: conditional UPDATE (user, OQ1 in plan.md)**: The outbox is written only by
  the job, so a Java check on the freshly loaded entity is enough there. The **code** row can
  change during TX 2, because a new code request runs `invalidateLive(email)` at the same time.
  If TX 2 has already read code A as `PENDING` and then writes `ACTIVE`, it overwrites
  `INVALIDATED`. Two live codes then exist for the email, and the partial unique index (001 R4)
  fails TX 2. So the guard lives in the database, in the statement that writes:
  - `activatePending(id, sentAt, expiresAt)`:
    `UPDATE verification_codes SET status = 'ACTIVE', sent_at = :sentAt, expires_at = :expiresAt
    WHERE id = :id AND status = 'PENDING'`
  - `invalidatePending(id)`:
    `UPDATE verification_codes SET status = 'INVALIDATED' WHERE id = :id AND status = 'PENDING'`

  Both are `@Modifying` repository methods that return the row count; 0 means the code was
  already replaced, and that is not an error. If the code request holds the row, the UPDATE
  waits for it, and under READ COMMITTED Postgres re-checks the `WHERE` against the committed
  row, so a replaced code matches nothing. The `VerificationCode` entity gets no `activate` or
  `invalidate` method, so there is only one way to make these changes (constitution III).
  `expiresAt` = `sentAt + tickgeek.auth.code-ttl`, computed in `CodeOutboxJobOwner`.
- **Alternatives considered**: Locking the code with `SELECT … FOR UPDATE` in TX 2 and then
  changing the entity. This also works, but it adds a lock wait and a second statement.

## R4. Email job ↔ code boundary: the `OutboxJobOwner` port (user)

- **Decision**: The email module defines `OutboxJobOwner` with three methods keyed by outbox id:
  `isStillWanted`, `delivered(outboxId, now)` and `abandoned(outboxId)`
  ([contracts/email-job.md](contracts/email-job.md)). The auth module implements it as
  `CodeOutboxJobOwner`. That class finds the code with `findByOutboxId`, which is served by the
  existing unique index `verification_codes_outbox_id_key` (V3). It activates or invalidates
  **that code by id**, never by email.
- **Rationale**: Keeps 001 R9 ("the email module knows nothing about codes"): the email module
  depends only on its own interface, and every arrow points from auth to email. Working by id
  means an attempt for code A can never touch a newer code B.
- **No code for an outbox** (cannot happen today, every outbox is a code email): `isStillWanted`
  returns true and the other two do nothing.
- **Limit**: There is one implementation. Routing to several owners (e.g. receipt emails) is left
  until a second email type exists.
- **Alternatives considered**: `EmailJobProcessor` uses `VerificationCodeRepository` directly
  (reverses R9, email then depends on auth); moving the processor into `module/auth/code` (each
  future email type would copy the send and retry logic).

## R5. Cancelling the job of a replaced code: lazy (user)

- **Decision**: The code request does **not** touch the old outbox. It only invalidates the old
  code (001 R4). The next time the old job runs, check 2 in R3 sees the code is not `PENDING` and
  cancels the outbox, which clears its payload.
- **Rationale**: Only the job ever writes an outbox row, so there is a single writer and no
  conflicting updates.
- **Consequences**:
  - No attempt starts after the code was replaced, so SC-007 holds.
  - The old outbox stays `PENDING`, with the plaintext code and the address in `payload`, until
    its next scheduled attempt (at most 40 s). After a restart (R1 gap) it is never cancelled and
    keeps its payload until the sweeper exists.
  - This differs from US4, which says the job is recorded as cancelled when code B is requested.
- **Alternatives considered**: Eager cancel in `CodeRequestService`, in the same transaction as
  `invalidateLive` (matches US4 and clears the payload at once, but adds a second writer and needs
  the "only if PENDING" guards on every outbox change).

## R6. Failures and backoff

- **Decision**: Any exception from `EmailSender.send` is a failed attempt. A **permanent
  recipient rejection is treated the same** and retried **(user)**. After a failure:
  - if `attempts < max-attempts`, then `lastError` = the error and
    `nextAttemptAt = now + retry-base-delay × 2^(attempts − 1)`. With 5 s that gives 5, 10, 20
    and 40 s (FR-004). A new `OutboxJobScheduled` is published with that delay.
  - otherwise the job is abandoned: the outbox goes to `ERROR` and the code to `INVALIDATED`, in
    one transaction (FR-007).

  `lastError` is the exception class and message. `EmailSender` implementations must not put the
  code or the payload in exception messages (FR-008). The backoff formula lives only in
  `Outbox.retryLater`.
- **Rationale**: The sender stays a plain interface, and every failure follows one path.
- **Consequences**: A rejected address costs 5 attempts and about 75 s before the code is
  invalidated. This differs from FR-007a, SC-006 and US3 scenario 3.
- **Alternatives considered**: A `RecipientRejectedException` that each sender maps to, followed
  by an immediate abandon (matches the spec, more work in each sender).

## R7. Which provider sent the email: logged only (user)

- **Decision**: Each `EmailSender` implementation logs its own name when a provider accepts.
  Nothing is stored and the schema does not change.
- **Consequences**: FR-006 asks for the provider to be "recorded" with the job. It is in the logs
  only and cannot be queried.
- **Alternatives considered**: An `outboxes.provider` column (needs a `V5` migration, and
  `send` must return the provider name).

## R8. Configuration (user)

- **Decision**: A new `EmailProperties` record bound to `tickgeek.email`:
  - `max-attempts: 5`
  - `retry-base-delay: 5s`

  The draft field `AuthProperties.verificationCodeMaxAttemptCount` and its
  `tickgeek.auth.verification-code-max-attempt-count` key are removed.
- **Rationale**: These settings belong to the email job. Keeping them out of `tickgeek.auth` keeps
  email independent of auth (R4). The old name also suggested
  `verification_codes.attempt_count`, which counts wrong-code entries.
- **Alternatives considered**: Keeping them in `AuthProperties` under `email-*` names. A
  `CHECK (attempts <= 5)` in the database (schema change, and the limit would then live in two
  places).

## R9. Email senders and profiles (user)

- **Decision**: `EmailSender` is the only way to send. `LoggingEmailSender` becomes
  `@Profile("local")` and logs through SLF4J instead of `System.out` (FR-012). No other
  implementation exists yet; Brevo comes in a later feature as its own `EmailSender`. It is
  the only provider (user, 2026-11-26), so there is no fallback order. Integration tests
  register a recording fake `EmailSender` in `IntegrationTestConfig`, because they do not run
  the `local` profile.
- **Rationale**: The plaintext code can never reach a non-local log (FR-008).
- **Risk, accepted (user, OQ2 in plan.md)**: `spring.main.lazy-initialization: true` is set, so
  a missing `EmailSender` probably does not fail at startup. It likely fails when the first job
  runs, after the code request has committed, which leaves the outbox `PENDING` with no retry.
  This is inferred, not checked. Starting the app without the `local` profile and requesting one
  code would confirm it. It is accepted for now because the app runs only locally. Revisit
  before the first deploy.
- **Sender input (user, OQ3 in plan.md)**: `send(String payload, String to)` (renamed from `sentEmail`) stays.
  `EmailJobProcessor` reads `to` with `CodeEmailComposer.recipientOf(payload)`. Each sender
  turns the payload into the email text (FR-011). Rejected: `send(String payload)`, where each
  sender would parse the JSON itself; `send(EmailMessage)`, with one composer building
  to/subject/body for all senders.
- **Alternatives considered**: A sender that always fails outside `local` (the app runs and jobs
  give up cleanly); `LoggingEmailSender` everywhere (breaks FR-008).

## R10. Testing

- **Unit tests** (Mockito, `MutableClock`):
  - `Outbox`: every transition and its "only if PENDING" no-op, and the backoff values.
  - `OutboxJobSteps`: each claim check in order, and each record path.
  - `EmailJobProcessor`: claim → send → record, and failure handling.
  - `CodeOutboxJobOwner`: wanted, delivered and abandoned, by id, plus the no-code case.
- **Integration tests** (Testcontainers + Awaitility, fake sender):
  - success activates the code with `expires_at = accept time + code-ttl` and clears the payload;
  - two failures then success;
  - five failures give up: `ERROR`, code `INVALIDATED`, no payload;
  - a replaced code is cancelled on its next run;
  - a retry for a replaced code starts no provider call;
  - the concurrency case from R3: a code replaced during TX 2 stays `INVALIDATED`, and TX 2 commits.
- The 80% new-code line coverage gate (JaCoCo, constitution V) applies.
