---

description: "Task list for Email Code Sign-In: Send the Code Email"
---

# Tasks: Email Code Sign-In: Send the Code Email

**Input**: Design documents from `/specs/002-sending-code/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/email-job.md](contracts/email-job.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Constitution Principle V requires service unit tests and at least 80% line
coverage on new code. research.md R10 lists the job scenarios that need integration tests.

**Spec deviations** (user decisions, plan.md): the old job is cancelled lazily (US4); a rejected
address is retried like any failure (FR-007a is **not** implemented); the provider is logged,
not stored (FR-006); retries do not survive a restart (FR-009). No task implements these spec
items.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: User story from spec.md (US1–US4)

## Path Conventions

- `BE/` = `tickgeek_backend/src/main/java/minhdoswe/tickgeek/`
- `BR/` = `tickgeek_backend/src/main/resources/`
- `BT/` = `tickgeek_backend/src/test/java/minhdoswe/tickgeek/`

## How the integration tests drive the job

`MutableClock` does not move on its own, and `EmailJobListener` schedules at
`clock.instant() + delay`. Integration tests replace the `TaskScheduler` with
`BT/support/ControllableTaskScheduler.java`:
- A task due by the test clock runs at once, on its pool. That covers the first attempt, which
  is due at the request time; tests wait for it with Awaitility.
- A task due later (a retry) is held. `AbstractEmailJobIT.runNextAttempt` moves the clock to
  `next_attempt_at` and runs it on the test thread.

So no attempt runs unless the test asks for it. (Revised during implementation: the first
version called `process` directly, which could race with the retry's own scheduled run.)

---

## Phase 1: Setup

- [X] T001 Retry configuration (research R8):
  - Add `BE/config/EmailProperties.java`, a record
    `@ConfigurationProperties("tickgeek.email")` with `int maxAttempts` and
    `Duration retryBaseDelay`. It is picked up by the existing `@ConfigurationPropertiesScan`.
  - Add the `tickgeek.email` block to `BR/application.yaml` and `BR/application-local.yaml` with
    `max-attempts: 5` and `retry-base-delay: 5s`.
  - Remove the field `verificationCodeMaxAttemptCount` from `BE/config/AuthProperties.java`, and
    remove the key `verification-code-max-attempt-count` from `BR/application.yaml`.
- [X] T002 [P] Test support:
  - Add `email()` to `BT/support/TestProperties.java`, returning `new EmailProperties(5,
    Duration.ofSeconds(5))`.
  - Add `BT/support/RecordingEmailSender.java`, an `EmailSender` that records each
    `(payload, to)` call. It can be set to accept, to fail the next N calls, or to always fail,
    and it throws `IllegalStateException("provider unavailable")` on a failure.
  - Register it as a `@Bean` in `BT/support/IntegrationTestConfig.java`.
  - Reset it in `AbstractIntegrationTest.resetState()`.

---

## Phase 2: Foundational

- [X] T003 [P] `BE/module/email/OutboxJobOwner.java`: an interface with
  `boolean isStillWanted(UUID outboxId)`, `void delivered(UUID outboxId, Instant acceptedAt)` and
  `void abandoned(UUID outboxId)`, with Javadoc from
  [contracts/email-job.md](contracts/email-job.md) (research R4).
- [X] T004 [P] Sender interface and the local sender (research R9, OQ3):
  - Rename `EmailSender.sentEmail` to `send(String payload, String to)` in
    `BE/module/email/emailsender/EmailSender.java`.
  - In `BE/module/email/emailsender/implement/LoggingEmailSender.java`:
    - make it a `@Component @Profile("local")`;
    - log through SLF4J instead of `System.out`;
    - log the email text: the 6-digit code, "valid for 10 minutes", in English (FR-011);
    - log the provider name `logging` on acceptance (R7).
- [X] T005 [P] `CodeEmailComposer.recipientOf(String payload)` in
  `BE/module/email/CodeEmailComposer.java`: read `to` from the JSON `{"to": …, "code": …}`.
  Test it in `BT/module/email/CodeEmailComposerTest.java`, including a round trip with
  `toPayload`.
- [X] T006 `BE/module/email/EmailJobListener.java`: rewrite the draft.
  - Make it a `@Component` with constructor injection of `TaskScheduler`, `EmailJobProcessor`
    and `Clock`.
  - `@TransactionalEventListener(phase = AFTER_COMMIT)` `dispatch(OutboxJobScheduled)` schedules
    `processor.process(outboxId)` at `clock.instant().plus(delay)`. Use `clock`, not
    `Instant.now()`.
  - Test it in `BT/module/email/EmailJobListenerTest.java` with a mock `TaskScheduler`: the
    scheduled time equals clock + delay, and the task calls `process` with the id.
  - Depends on T015 for `EmailJobProcessor.process`. Write both in the same change if needed.

---

## Phase 3: User Story 1 - The code email is delivered (Priority: P1) 🎯 MVP

**Goal**: After a code request, the email goes out once and the code becomes `ACTIVE`, expiring
`code-ttl` after the provider accepted.

**Independent Test**: Request a code for `ana@example.com` with `RecordingEmailSender`
accepting. Then the sender got 1 call with `to = ana@example.com`; the outbox is `SENT` with
`attempts = 1`, `published_at` set and `payload` NULL; the code is `ACTIVE` with `sent_at =
published_at` and `expires_at = sent_at + 10 min`.

### Tests for User Story 1

- [X] T007 [P] [US1] `BT/module/email/OutboxTest.java`:
  - `claim()` increments `attempts`.
  - `succeed(now)` on a `PENDING` outbox: status `SENT`, `publishedAt = now`, `payload` null.
  - `succeed` on a non-`PENDING` outbox changes nothing ("only if PENDING", data-model.md).
- [X] T008 [P] [US1] `BT/module/auth/code/CodeOutboxJobOwnerTest.java` (mock repository,
  `TestProperties.auth()`):
  - `isStillWanted`: true for a `PENDING` code, false for `ACTIVE`, `USED` and `INVALIDATED`,
    true when `findByOutboxId` is empty.
  - `delivered(outboxId, at)` calls `activatePending(codeId, at, at + 10 min)`.
  - `abandoned(outboxId)` calls `invalidatePending(codeId)`.
  - With no code, both do nothing.
- [X] T009 [P] [US1] Service tests (mocks, `MutableClock`):
  - `BT/module/email/OutboxJobStepsTest.java`:
    - `claim` returns empty and does not claim when the outbox is not `PENDING`, or when
      `next_attempt_at > now`;
    - otherwise it claims and returns the payload;
    - `recordSuccess(id, now)` calls `outbox.succeed(now)` and `owner.delivered(id, now)`.
  - `BT/module/email/EmailJobProcessorTest.java`:
    - claim → `send(payload, recipientOf(payload))` → `recordSuccess` with the clock time
      after the send;
    - nothing is sent when the claim is empty.
- [X] T010 [P] [US1] `BT/integration/CodeEmailDeliveryIT.java`: the Independent Test above,
  through `requestCode` and Awaitility. Also assert that a second
  `EmailJobProcessor.process(outboxId)` sends nothing (FR-010).

### Implementation for User Story 1

- [X] T011 [P] [US1] `BE/module/email/Outbox.java`:
  - Replace `checkIfAgainstThreshold` with `claim()` (`attempts++`) and `succeed(Instant now)`
    (`PENDING` → `SENT`, `publishedAt = now`, `payload = null`).
  - Every change does nothing unless `status == PENDING`.
  - Add `getNextAttemptAt()`.
- [X] T012 [P] [US1] `BE/module/auth/code/VerificationCodeRepository.java` (research R3; load
  the `supabase-postgres-best-practices` skill first):
  - `Optional<VerificationCode> findByOutboxId(UUID outboxId)`, which uses the unique index
    `verification_codes_outbox_id_key`.
  - `@Modifying int activatePending(Long id, Instant sentAt, Instant expiresAt)` running
    `UPDATE verification_codes SET status = 'ACTIVE', sent_at = :sentAt, expires_at = :expiresAt
    WHERE id = :id AND status = 'PENDING'`.
  - `@Modifying int invalidatePending(Long id)` running
    `UPDATE verification_codes SET status = 'INVALIDATED' WHERE id = :id AND status = 'PENDING'`.
  - Use native queries, like `invalidateLive`. A result of 0 is not an error.
  - Do not add `activate` or `invalidate` methods to the `VerificationCode` entity.
- [X] T013 [US1] `BE/module/auth/code/CodeOutboxJobOwner.java`:
  - A `@Component` implementing `OutboxJobOwner`; every method is
    `@Transactional(propagation = MANDATORY)`.
  - `isStillWanted`: the code is `PENDING`, or no code exists.
  - `delivered`: `activatePending(id, acceptedAt, acceptedAt.plus(authProperties.codeTtl()))`.
  - `abandoned`: `invalidatePending(id)`.
  - It always finds the code with `findByOutboxId` and never changes a code by email
    (contracts/email-job.md). Depends on T003, T012.
- [X] T014 [US1] `BE/module/email/OutboxJobSteps.java`:
  - A `@Component` with `@Transactional` methods (research R2).
  - `Optional<String> claim(UUID outboxId, Instant now)` loads the outbox and checks, in order:
    1. not `PENDING` → empty;
    2. `next_attempt_at > now` → empty;
    3. otherwise `outbox.claim()` and return the payload.
  - Leave a place for the US3 and US4 checks in the order from research R3.
  - `recordSuccess(UUID outboxId, Instant now)`: reload the outbox, call `succeed(now)` and then
    `owner.delivered(outboxId, now)`, in one transaction.
  - Depends on T003, T011.
- [X] T015 [US1] `BE/module/email/EmailJobProcessor.java`: rewrite the draft.
  - Make it a `@Component` with constructor injection of `OutboxJobSteps`, `EmailSender`,
    `CodeEmailComposer` and `Clock`. Remove the comment placeholders.
  - `process(UUID outboxId)`:
    - `steps.claim(id, clock.instant())`; if empty, return;
    - with no transaction open, call `sender.send(payload, composer.recipientOf(payload))`;
    - then `steps.recordSuccess(id, clock.instant())`.
  - The method must not be `@Transactional`.
  - Depends on T004, T005, T014.

**Checkpoint**: The MVP works. Code emails are sent and codes become live.

---

## Phase 4: User Story 2 - Retry with growing waits (Priority: P1)

**Goal**: A failed attempt is retried after 5, 10, 20 and 40 s, and each retry runs the whole
send again.

**Independent Test**: The sender fails the first 2 calls. Then:
- after attempt 1: `PENDING`, `attempts = 1`, `next_attempt_at = start + 5 s`, `last_error` set;
- after a 5 s advance and attempt 2: `next_attempt_at = then + 10 s`;
- after a 10 s advance and attempt 3: `SENT`, and the code is `ACTIVE`, expiring 10 min after
  that attempt.

### Tests for User Story 2

- [X] T016 [P] [US2] Unit tests:
  - In `BT/module/email/OutboxTest.java`: `retryLater(now, error, 5 s)` after attempts 1, 2, 3
    and 4 sets `nextAttemptAt` to now + 5, 10, 20 and 40 s and sets `lastError`. It does nothing
    when not `PENDING`.
  - In `BT/module/email/OutboxJobStepsTest.java`: `recordFailure` with attempts < max calls
    `retryLater` and publishes `OutboxJobScheduled(id, backoff)`.
  - In `BT/module/email/EmailJobProcessorTest.java`: a sender exception leads to
    `recordFailure(id, now, "<ExceptionSimpleName>: <message>")`, and the payload is not in the
    error text.
- [X] T017 [P] [US2] `BT/integration/CodeEmailRetryIT.java`: the Independent Test above. Use
  `runNextAttempt` (see "How the integration tests drive the job").
  Assert 3 `send` calls.

### Implementation for User Story 2

- [X] T018 [US2] `Outbox.retryLater(Instant now, String error, Duration baseDelay)` in
  `BE/module/email/Outbox.java`:
  - `lastError = error`;
  - `nextAttemptAt = now + baseDelay × 2^(attempts − 1)`; this is the only place the backoff
    formula lives (constitution III);
  - only if `PENDING`.
- [X] T019 [US2] `OutboxJobSteps.recordFailure(UUID outboxId, Instant now, String error)` in
  `BE/module/email/OutboxJobSteps.java`:
  - reload the outbox;
  - if `attempts < emailProperties.maxAttempts()`, call `retryLater` and publish
    `OutboxJobScheduled(id, nextAttemptAt − now)` with `ApplicationEventPublisher` inside the
    transaction, so it fires after commit (research R1, R6).
  - Depends on T018.
- [X] T020 [US2] In `BE/module/email/EmailJobProcessor.java`, wrap the `send` call:
  - catch `Exception` and call `steps.recordFailure(id, clock.instant(),
    e.getClass().getSimpleName() + ": " + e.getMessage())`;
  - never log or store the payload.

**Checkpoint**: US1 and US2 work. Provider hiccups are retried.

---

## Phase 5: User Story 3 - Give up and invalidate the code (Priority: P1)

**Goal**: After the 5th failed attempt, the outbox becomes `ERROR` with the last error and no
payload, and the code becomes `INVALIDATED`, both in one transaction. A rejected address is
handled like any failure (deviation from FR-007a, research R6).

**Independent Test**: The sender always fails. After 5 attempts (5 `send` calls), the outbox is
`ERROR`, `payload` is NULL and `last_error` is the 5th failure with no code in it. The code is
`INVALIDATED`, and a 6th `process` call sends nothing.

### Tests for User Story 3

- [X] T021 [P] [US3] Unit tests:
  - In `BT/module/email/OutboxTest.java`: `giveUp(error)` → `ERROR`, `lastError`, payload null,
    and it does nothing when not `PENDING`.
  - In `BT/module/email/OutboxJobStepsTest.java`:
    - `recordFailure` at attempts == max calls `giveUp(error)` and `owner.abandoned(id)` and
      publishes no event;
    - `claim` with attempts >= max calls `giveUp("max attempts reached")` and
      `owner.abandoned(id)`, and returns empty.
- [X] T022 [P] [US3] `BT/integration/CodeEmailGiveUpIT.java`: the Independent Test above. Advance
  the clock 5, 10, 20 and 40 s between attempts.

### Implementation for User Story 3

- [X] T023 [US3] `Outbox.giveUp(String error)` in `BE/module/email/Outbox.java`: `PENDING` →
  `ERROR`, `lastError = error`, `payload = null`.
- [X] T024 [US3] In `BE/module/email/OutboxJobSteps.java`:
  - `recordFailure`: when `attempts >= maxAttempts`, abandon with `giveUp(error)` +
    `owner.abandoned(id)`.
  - `claim`: add check "attempts >= maxAttempts → abandon with the constant
    `MAX_ATTEMPTS_REACHED = "max attempts reached"`" after the due check (research R3 order).
  - Depends on T023.

**Checkpoint**: US1–US3 work. A code that can never arrive is invalidated.

---

## Phase 6: User Story 4 - Stop sending a replaced code (Priority: P2)

**Goal**: An attempt never starts for a code that has been replaced. Its job is cancelled the
next time it runs (lazy, research R5).

**Independent Test**: Code A's first attempt fails. Advance the clock past the 60 s cooldown and
request code B. A's next `process` call sends nothing, and outbox A is `CANCELLED` with `payload`
NULL. B's outbox is `SENT` and code B is `ACTIVE`.

### Tests for User Story 4

- [X] T025 [P] [US4] Unit tests:
  - In `BT/module/email/OutboxTest.java`: `cancel()` → `CANCELLED`, payload null, and it does
    nothing when not `PENDING`.
  - In `BT/module/email/OutboxJobStepsTest.java`: `claim` when `owner.isStillWanted(id)` is
    false calls `cancel()`, returns empty and leaves `attempts` unchanged.
- [X] T026 [P] [US4] `BT/integration/CodeEmailReplacedIT.java`:
  - the Independent Test above;
  - the race from research R3: claim code A's job, set code A to `INVALIDATED` with
    `JdbcTemplate`, then call `OutboxJobSteps.recordSuccess`. Code A stays `INVALIDATED`,
    `activatePending` returns 0, the transaction commits, and the outbox is `SENT`.

### Implementation for User Story 4

- [X] T027 [US4] Cancelling:
  - `Outbox.cancel()` in `BE/module/email/Outbox.java`: `PENDING` → `CANCELLED`,
    `payload = null`.
  - In `BE/module/email/OutboxJobSteps.java`, `claim` checks
    `!owner.isStillWanted(id) → outbox.cancel()` right after the `PENDING` check. That is check
    2 in research R3, before the due check.

**Checkpoint**: All four stories work.

---

## Phase 7: Polish & Cross-Cutting

- [X] T028 [P] `BT/integration/ApplicationContextIT.java`: assert that a `TaskScheduler` bean,
  `EmailJobListener` and `EmailJobProcessor` exist (research R1 "To verify").
- [X] T029 [P] Check every new and changed method against constitution I (≤ 30 lines, ≤ 3
  parameters, no magic values) and check that no log line or `last_error` outside
  `LoggingEmailSender` can contain the payload (FR-008).
- [ ] T030 Run `cd tickgeek_backend && ./mvnw verify` (tests plus the 80% coverage gate), then
  the manual scenarios in [quickstart.md](quickstart.md).

---

## Dependencies & Execution Order

- Setup (T001–T002) → Foundational (T003–T006) → US1 → US2 → US3 → US4 → Polish.
- T006 and T015 depend on each other: the listener calls `process`. Do them together.
- US2–US4 each edit `Outbox.java`, `OutboxJobSteps.java` and their tests, so do the stories in
  order, not in parallel.
- Within each story, write the tests first and see them fail.

## Parallel Examples

- Setup: T001 ∥ T002.
- Foundational: T003 ∥ T004 ∥ T005.
- US1:
  - tests T007 ∥ T008 ∥ T009 ∥ T010;
  - then T011 ∥ T012;
  - then T013 → T014 → T015.
- US2: T016 ∥ T017, then T018 → T019 → T020.
- US3: T021 ∥ T022, then T023 → T024.
- US4: T025 ∥ T026, then T027.
- Polish: T028 ∥ T029, then T030.

## Implementation Strategy

1. **MVP = US1** (T001–T015). Codes get sent locally and become live.
2. Add US2 (retries), then US3 (give up). These finish the P1 scope.
3. Add US4 (cancel replaced codes, P2).
4. Polish and run the full verify.
