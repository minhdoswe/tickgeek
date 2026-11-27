# Implementation Plan: Email Code Sign-In: Send the Code Email

**Branch**: `feat/send_confirmation_email` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-sending-code/spec.md`, plus the flow design and
decisions the user approved on 2026-11-26

## Summary

When a code request commits, `EmailJobListener` receives `OutboxJobScheduled` and schedules the
job on Spring's `TaskScheduler`. Each attempt has three steps:

1. A short transaction checks the outbox and its code, then claims the attempt.
2. The email goes out through the `EmailSender` interface, with no transaction open.
3. A second short transaction records the result.

On success, the outbox becomes `SENT` and the code `ACTIVE`, expiring 10 minutes after the
provider accepted. On failure, the job retries after 5, 10, 20 and 40 s; after 5 attempts the
outbox becomes `ERROR` and the code `INVALIDATED`. If the code was replaced, the job is cancelled
on its next run. The email module reaches codes only through the `OutboxJobOwner` port, which the
auth module implements.

No schema change. Details: [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (LTS)

**Primary Dependencies**: Spring Boot 4.1.1 (Data JPA, scheduling with `TaskScheduler`,
transactional event listeners), PostgreSQL JDBC driver. Lombok 1.18.36 was added to `pom.xml`
by the draft code (see Constitution Check).

**Storage**: PostgreSQL, existing `verification_codes` and `outboxes` tables (V1–V4); no new
migration

**Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL), Awaitility,
`MutableClock`; JaCoCo 80% line coverage gate on `mvn verify`

**Target Platform**: Linux server (backend only; no frontend change)

**Project Type**: Web application, backend part only

**Performance Goals**: A provider accepts 95% of code emails within 5 s of the request while
Brevo works (SC-001); all 5 attempts finish within about 75 s (SC-004)

**Constraints**:
- No transaction is open during a provider call.
- The plaintext code is never written to logs or `last_error` outside the `local` profile.
- Every state change applies only while its row is `PENDING`.
- A single running instance is assumed until the sweeper exists (research R1).

**Scale/Scope**: Up to 1,000 code emails per hour (the 001 global cap); no endpoints, no screens

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v2.0.0, Principles I–V and the Technology Stack.

| Principle | Gate | Status |
|---|---|---|
| I. Clean Code | Methods ≤ 30 lines, ≤ 3 params, no magic values, no dead code | PASS by design. `retryLater(now, error, baseDelay)` has 3 params. The limits come from `EmailProperties`; `"max attempts reached"` is a named constant. The draft `EmailJobProcessor` holds comment placeholders, which must be gone before merge |
| II. SOLID | Logic in services, data access in repositories, constructor injection, extend with new classes, small interfaces | PASS. `EmailJobProcessor` and `OutboxJobSteps` hold the logic; each sender is its own `EmailSender` class; `OutboxJobOwner` has 3 methods for one client. Lombok's `@RequiredArgsConstructor` still produces constructor injection |
| III. DRY | One source of truth per rule | PASS. The backoff formula lives only in `Outbox.retryLater`, the attempt limit only in `tickgeek.email.max-attempts`, and code activation only in `VerificationCode.activate` |
| IV. Frontend Standards | — | N/A, no frontend change |
| V. Testing Discipline | Unit tests for services, ≥ 1 integration test per endpoint, ≥ 80% new-code coverage | PASS. Unit tests for `Outbox`, `OutboxJobSteps`, `EmailJobProcessor` and `CodeOutboxJobOwner`; integration tests for the job flows (R10). There is no new endpoint |
| Technology Stack | No new language, framework or datastore | PASS for the design: Spring and PostgreSQL only. Open: Lombok is a library, not a framework, so this plan reads it as allowed. That is an interpretation; the constitution does not mention libraries |

Re-check after Phase 1: unchanged.

## Spec deviations (decided by the user, 2026-11-26)

These make the plan differ from [spec.md](spec.md). The spec text has not been changed yet.

| Spec item | Plan | Research |
|---|---|---|
| US4 scenarios 1–2: the old job is recorded as cancelled, with no code or address, when the new code is requested | Cancelled when the old job next runs (lazy); until then the payload stays | R5 |
| FR-007a, SC-006, US3 scenario 3: a rejected address stops at once | Retried like any other failure | R6 |
| FR-006: the job is recorded with the provider that sent it | The provider is logged, not stored | R7 |
| FR-009 and the restart and multi-instance edge cases | Not met: retries live in memory. A sweeper comes in a later feature | R1 |

## Resolved questions (user, 2026-11-26)

- **OQ1: Enforcing "only if PENDING" on the code row.** A new code request can invalidate code A
  while TX 2 activates it. Resolved: conditional UPDATEs, `activatePending` and
  `invalidatePending`, each `WHERE id = :id AND status = 'PENDING'` (research R3).
- **OQ2: A missing `EmailSender` outside `local`.** Resolved (user, 2026-11-26): accepted with
  no startup check. The app runs only locally for now, so nothing runs outside `local`. Revisit
  this before the first deploy (research R9).
- **OQ3: The `EmailSender` signature.** Resolved (user, 2026-11-26): the signature stays
  `(String payload, String to)`. The processor reads `to` from the payload with a new
  `CodeEmailComposer.recipientOf(payload)`. Each sender builds the email text itself
  (FR-011: the 6-digit code, "valid for 10 minutes", English). `sentEmail` is renamed to `send`.

## Project Structure

### Documentation (this feature)

```text
specs/002-sending-code/
├── spec.md              # What is built
├── plan.md              # This file
├── research.md          # Design decisions R1–R10
├── data-model.md        # Columns written, state changes, configuration
├── quickstart.md        # How to validate end to end
├── contracts/
│   └── email-job.md     # OutboxJobScheduled, EmailSender, OutboxJobOwner
├── tasks.md             # Created by /speckit-tasks
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
tickgeek_backend/
├── pom.xml                                   # Lombok added by the draft
├── src/main/resources/
│   └── application.yaml                      # + tickgeek.email.*; − verification-code-max-attempt-count
├── src/main/java/minhdoswe/tickgeek/
│   ├── config/
│   │   ├── AuthProperties.java               # draft field removed (R8)
│   │   ├── EmailProperties.java              # NEW: max-attempts, retry-base-delay
│   │   └── SchedulingConfig.java             # @EnableScheduling (draft)
│   └── module/
│       ├── auth/code/
│       │   ├── VerificationCodeRepository.java # + findByOutboxId, activatePending, invalidatePending (R3)
│       │   └── CodeOutboxJobOwner.java       # NEW: implements OutboxJobOwner (R4)
│       └── email/
│           ├── Outbox.java                   # + claim, succeed, retryLater, giveUp, cancel; draft method replaced
│           ├── OutboxJobOwner.java           # NEW: port (R4)
│           ├── OutboxJobSteps.java           # NEW: @Transactional claim / recordSuccess / recordFailure (R2, R3)
│           ├── EmailJobProcessor.java        # draft → claim, send, record
│           ├── EmailJobListener.java         # draft → AFTER_COMMIT → TaskScheduler at clock + delay
│           └── emailsender/
│               ├── EmailSender.java          # interface (OQ3)
│               └── implement/LoggingEmailSender.java # @Profile("local"), SLF4J (R9)
└── src/test/java/minhdoswe/tickgeek/
    ├── module/email/...                      # OutboxTest, OutboxJobStepsTest, EmailJobProcessorTest
    ├── module/auth/code/...                  # CodeOutboxJobOwnerTest
    ├── integration/                          # code email job ITs (R10)
    └── support/
        ├── IntegrationTestConfig.java        # + fake EmailSender bean
        └── TestProperties.java               # + email() defaults
```

**Structure Decision**: The same layout as 001: the backend is organized by domain module under
`minhdoswe.tickgeek.module`. The email job lives in `module/email`; code-specific reactions
live in `module/auth/code` behind the `OutboxJobOwner` port.

## Complexity Tracking

No constitution violations to justify. Open item: the Lombok reading under Technology Stack.
Watch for later: under OQ3, the Brevo sender and `LoggingEmailSender` would
each build the same email text. To keep constitution III (DRY), they should share one text
builder.
