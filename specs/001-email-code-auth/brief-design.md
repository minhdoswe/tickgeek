# Design Brief: Email Code Sign-In

The 20% of [research.md](research.md), [data-model.md](data-model.md),
[contracts/](contracts/) and [plan.md](plan.md). It covers **how** we build it. Read
[brief-spec.md](brief-spec.md) first. IDs: `Rxx` = research decision, `FR-xxx` = spec rule.

> **Before you read:** for each spec rule, guess how you'd enforce it (DB? lock? config?).
> Compare after.

## 1. Shape

Next.js (browser-facing) → Spring Boot API → PostgreSQL. Tokens stay in Next.js HttpOnly
cookies. PostgreSQL holds all state. Code emails go out from an in-process `@Async` pool, with
no message broker. `R1`, `R13`, `R21`

## 2. Flow → code

Backend root: `tickgeek_backend/src/main/java/minhdoswe/tickgeek/`

| Step | Endpoint | Code | IDs |
|---|---|---|---|
| Ask code | `POST /api/auth/code-requests` | `CodeRequestService.request`: lock source → lock email → 4 limits → kill old code → save `PENDING` → outbox row + event | R4, R5, R7, R21 |
| Send email | (after commit) | `OutboxJobDispatcher` (planned, `@Async`) → `OutboxJobProcessor.process(id)`: lock row; `PENDING` and due? Brevo → code `ACTIVE`; fail → retry at `now + backoff` | R9, R16, R21 |
| Check code | `POST /code-verifications` | `CodeVerificationService.verify`: row lock → count wrong / mark `USED` → sign in **or** `RegistrationService.grant` | R4, R8 |
| Name step | `POST /registrations` | `RegistrationService.complete`: use token once → insert user `ON CONFLICT DO NOTHING` → tokens | R8 |
| Sign out | `POST /sign-out` | `RefreshTokenService.revoke`: always 204 | R17 |

## 3. Tables

| Table | Holds | Key point |
|---|---|---|
| `users` | Finished accounts only | email normalized + unique, no password |
| `verification_codes` | Code per **email** (not user) | HMAC stored; `PENDING → ACTIVE → USED/INVALIDATED`; one live per email |
| `registration_permissions` | "verified, may create account" | SHA-256 token, one use, dies with the code |
| `refresh_tokens` | One per device (= Session) | valid if not revoked and used in the last 30 days |
| `outboxes` | Emails to send | source of truth for delivery; payload erased when finished; `next_attempt_at` = when due |

## 4. Big choices

| Choice | Instead of | Trade-off we accept | ID |
|---|---|---|---|
| JWT (15 min) + DB refresh token (30 days) | DB check on every request | copied JWT works ≤ 15 min after sign-out | R1 |
| Limits = count rows; lock per source, then per email | one global lock | same IP or same email still queue | R5 |
| System-wide count runs unlocked | a 3rd global lock, a slot table | a burst passes the cap by ≤ requests in flight; other caps stay exact | R5 |
| Outbox row + after-commit event + `@Async` pool; retries on an in-process scheduler | RabbitMQ (R18–R20); sweeper-driven retries | in-memory tasks and retries die on restart; `OutboxSweeper` sends them ~4 min late | R20, R21 |
| Pool full (4 threads, queue 100) → log and drop | `CallerRunsPolicy` | that email waits for the sweeper; requests never wait on Brevo (FR-004) | R21 |
| IP only from the trusted proxy header | read `X-Forwarded-For` | needs correct deploy config | R6 |

## 5. Risks → tests that guard them

Tests are in `tickgeek_backend/src/test/java/minhdoswe/tickgeek/integration/`. `R14`

| Risk | Test |
|---|---|
| Two live codes for one email; one IP beats its cap | `CodeRequestConcurrencyIT.parallelRequestsForOneEmailLeaveOneLiveCode`, `oneSourceGetsExactlyTwentyAccepted` |
| One user's lock makes other users wait | `CodeRequestConcurrencyIT.aHeldLockBlocksOnlyRequestsForTheSameEmail` |
| A burst overshoots the global cap past its bound | `GlobalLimitConcurrencyIT.systemWideCapIsExceededByAtMostTheParallelThreads` |
| Parallel guesses beat 5 tries; a code verifies twice | `CodeVerificationEndpointIT.parallelWrongGuessesCountAtMostFive`, `parallelCorrectVerificationsSucceedOnce` |
| Two name submits make two accounts | `RegistrationEndpointIT.parallelRegistrationsForOneEmailCreateOneAccount` |
| Fake IP header bypasses limits | `UntrustedProxyForwardingIT.spoofedForwardedForIsIgnored` |
| Old code email still sent / activates | `OutboxConsumerIT.replacedCodeIsCancelledAndNeverSent`, `codeReplacedWhileSendingStaysInvalidated` |
| Rollback sends; duplicate sends twice; lost task never sent | `EmailMessagingIT` (`aRolledBackJobIsNeverPublished`, `theSameMessageDeliveredTwiceSendsOneEmail`, `aJobWhoseMessageWasLostIsRepublishedByTheSweeper`), to port to R21 |
| Full email pool loses the email | none yet (R14 "Email dispatch") |
| `/me` reachable without JWT | `SecurityMatchersIT` |

## 6. Decisions: check you agree

1. Accept a ≤ 15 min access window after sign-out? (`R1`)
2. OK that an attacker using up the global cap blocks sign-ins ≤ 1 h? (`R5`)
3. OK that a burst can send a few emails past the 1,000 global cap? (`R5`)
4. OK that a restart or a full email pool delays that email ≤ ~4 min? Change if you want a bigger pool or shorter `sweep-interval`. (`R20`, `R21`)

## 7. Quiz

1. Why is `verification_codes` keyed by email and not `user_id`?
2. Ana and Bob, on different networks, request codes at once. Does either wait? What if both are for Ana's email?
3. The app crashes after a code request commits, before sending. What did the user see; does the email go out?
4. Brevo is slow and 150 requests arrive in one second. What happens to request 150?

<details><summary>Answers</summary>

1. A user lookup at request time would leak who has an account (`R7`).
2. Neither waits. Same email: the second waits on the email lock, then the cooldown refuses it (`R5`).
3. "Code sent". The row stays `PENDING`; a sweeper sends it ~4 min after an instance runs again (`R20`, `R21`).
4. "Code sent" at once; the full pool drops its task; the sweeper sends it (`R21`).

</details>
