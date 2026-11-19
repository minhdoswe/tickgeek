# Implementation Plan: Email Code Sign-In and Registration

**Branch**: `001-email-code-auth` | **Date**: 2026-11-11, revised 2026-11-13 (email delivery via
RabbitMQ, R18–R20), 2026-11-16 (per-source and per-email code-request locks, R5) and 2026-11-16
(RabbitMQ removed; in-process `@Async` email dispatch, R21) |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-email-code-auth/spec.md`

## Summary

A single passwordless flow for organizers. The user enters an email, receives a 6-digit code,
and enters it. Existing users are signed in; new users enter their first and last name and then
are signed in. The backend (Spring Boot) is stateless for API requests: it checks a 15-minute
JWT access token by signature alone. A refresh token stored in the database (one per device)
provides sign-out and the 30-day timeout that restarts with activity. Codes, rate limits,
registration permissions and refresh tokens live in PostgreSQL. Each code
email is saved as an outbox row in the request's transaction; after commit, a Spring event hands
the row's id to a dedicated `@Async` thread pool, which sends it through Brevo straight away.
Failed sends are retried by an in-process scheduler, and a slow sweeper sends any job whose
in-memory task was lost (restart, full pool). The Next.js frontend talks to the backend only from
server code and keeps both tokens in HttpOnly cookies. Account existence is checked only after a code verifies, so nothing before
that point can reveal it. Details: [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (LTS); TypeScript on Node.js 26 LTS

**Primary Dependencies**: Spring Boot 4.1.x (Web MVC, Security, OAuth2 Resource Server for
JWT encode/decode via Nimbus, Data JPA, Validation, Flyway; Spring `@Async` and
`ThreadPoolTaskScheduler` for email dispatch), PostgreSQL JDBC driver,
Spring `RestClient` for Brevo; Next.js 16.4.x (App Router, Server Actions), React 19

**Storage**: PostgreSQL (system of record); schema in Flyway migration
`V1__create_ticket_system.sql` (V2 merged into V1). No message broker: email jobs live in the
`outboxes` table and are dispatched in-process (R21).

**Testing**: JUnit 5, Mockito, Spring Boot Test + MockMvc, Testcontainers (PostgreSQL),
Awaitility for asynchronous assertions, JaCoCo (80% line coverage gate on
`mvn verify`); frontend `npm run lint` + `npm run build`

**Target Platform**: Linux server (backend), Node.js server + modern browsers (frontend)

**Project Type**: Web application (separate backend and frontend projects)

**Performance Goals**: Auth endpoints respond in < 300 ms p95 at V1 volume; code email handed
to Brevo within 2 s of the request on the normal path, and within about 4 min of the app running
again after a restart or of the email pool overflowing (supports SC-002's 1-minute arrival
target)

**Constraints**: No identifiable difference between registered and unregistered emails before
verification (FR-004); infrastructure limited to PostgreSQL (constitution v2.0.0 also allows
RabbitMQ, which this feature no longer uses); no plaintext codes or tokens at rest except the
code inside an unsent outbox payload (removed after sending); in-process job events carry only
the outbox id; a restart or a full email pool must not fail a code request or lose its email,
and the request thread never waits for Brevo; the cooldown, per-email and per-source
limits are exact under concurrency, while the system-wide cap may be exceeded by about the number
of code requests in flight at once (R5)

**Scale/Scope**: Hundreds to low thousands of organizers; up to 1,000 code emails per hour
system-wide (configurable cap); 6 endpoints, 4 pages

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v2.0.0, Principles I–V and the Technology Stack. The design uses only Spring Boot
and PostgreSQL, so the stack gate passes. RabbitMQ stays on the approved stack (added in v1.1.0)
but is no longer used; removing that line is a separate `/speckit-constitution` decision. v2.0.0 (2026-11-16)
reduced Principle III to DRY; the KISS/YAGNI rules it removed no longer gate this plan.

| Principle | Gate | Pre-design | Post-design |
|---|---|---|---|
| I. Clean Code | Methods ≤ 30 lines, ≤ 3 params, no magic numbers, no dead code | PASS (planned) | PASS. All limits and durations are config values (data-model "Configuration values"). Multi-value inputs use records, e.g. `CodeRequestContext` passed to `CodeRequestLimit.check(context)` |
| II. SOLID | Controller → service → repository; constructor injection; extend with new classes; small interfaces | PASS (planned) | PASS. One controller (`AuthController`, HTTP only); services per use case; limits are separate `CodeRequestLimit` classes; email via one-method `EmailSender` (Brevo + logging versions); verification outcome is a sealed `VerificationOutcome` with a `toResponse()` method, so the controller doesn't switch on types. Email dispatch split by job: `CodeEmailQueue` (save + announce), `OutboxJobDispatcher` (after-commit, `@Async`: run now or schedule a retry), `OutboxJobProcessor` (send), `OutboxSweeper` (recover), `EmailAsyncConfig` (executor + retry scheduler beans); `CodeRequestService` only knows `CodeEmailQueue` |
| III. DRY | Each business rule, validation, and constant has a single source of truth | PASS | PASS. Every limit value, duration and pool size is defined once in configuration (data-model "Configuration values"); each limit rule lives in one `CodeRequestLimit` class; the code-request lock namespaces and their order live only in `CodeRequestLockRepository` (R5) |
| IV. Frontend Standards | No `any`; Server Components by default; one component per file ≤ 200 lines; API calls only in `lib/api/` | PASS (planned) | PASS. Only `EmailForm`, `CodeForm`, `NameForm`, `ResendButton`, `SignOutButton` are client components; Server Actions call `lib/api/auth.ts` |
| V. Testing Discipline | Unit tests for services; ≥ 1 integration test per endpoint; ≥ 80% new-code coverage | PASS (planned) | PASS. 6 endpoints → integration test classes per endpoint, plus the required risky-boundary tests in research R14 (including the in-process email dispatch tests of R21; PostgreSQL is the only Testcontainer); services unit-tested with mocks; JaCoCo gate in the build |

No violations; Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-email-code-auth/
├── plan.md              # This file
├── research.md          # Phase 0: design decisions R1–R21
├── data-model.md        # Phase 1: tables, states, configuration
├── quickstart.md        # Phase 1: how to validate end to end
├── contracts/
│   └── auth-api.md      # Phase 1: REST endpoints, errors, frontend routes, cookies
│                        # (email-messaging.md removed 2026-11-16 with RabbitMQ, R21)
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
tickgeek_backend/
├── pom.xml                                   # add web, security, jpa, validation, flyway,
│                                             # postgresql, testcontainers (postgresql),
│                                             # awaitility, jacoco; no amqp (R21)
├── src/main/resources/
│   ├── application.yaml                      # tickgeek.auth.* / tickgeek.email.* config,
│                                             # (incl. tickgeek.email.executor.*), datasource, proxies
│   ├── application-local.yaml                # logging email sender
│   └── db/migration/
│       └── V1__create_ticket_system.sql      # full schema, auth tables included
├── src/main/java/minhdoswe/tickgeek/
│   ├── TickgeekApplication.java
│   ├── common/
│   │   ├── GlobalExceptionHandler.java       # one error body shape
│   │   ├── ErrorResponse.java
│   │   └── TokenGenerator.java               # SecureRandom tokens + SHA-256 hashing
│   ├── config/
│   │   ├── SecurityConfig.java               # 4 explicit public endpoints; /me and all
│   │   │                                     # other /api/** authenticated (research R17)
│   │   ├── JwtConfig.java                    # HS256 JwtEncoder/JwtDecoder from TICKGEEK_JWT_SECRET
│   │   ├── EmailAsyncConfig.java             # @EnableAsync; email executor (bounded, discard +
│   │   │                                     # log when full) and retry scheduler beans (R21)
│   │   └── AuthProperties.java               # @ConfigurationProperties tickgeek.auth
│   └── module/
│       ├── auth/
│       │   ├── AuthController.java
│       │   ├── code/                         # CodeRequestService, CodeVerificationService,
│       │   │                                 # VerificationCode(+Repository), CodeHasher
│       │   ├── limit/                        # CodeRequestLimit + 4 implementations
│       │   ├── registration/                 # RegistrationService, RegistrationPermission(+Repository)
│       │   ├── token/                        # AccessTokenIssuer (JWT), RefreshTokenService,
│       │   │                                 # RefreshToken(+Repository), TokenPair
│       │   └── dto/                          # request/response records
│       ├── user/                             # User, UserRepository
│       ├── security/                         # AuthDataCleanupJob (retention job)
│       └── email/                            # EmailSender, BrevoEmailSender,
│                                             # LoggingEmailSender, Outbox(+Repository),
│                                             # CodeEmailComposer, CodeEmailQueue,
│                                             # OutboxJobScheduled (event), OutboxJobDispatcher,
│                                             # OutboxJobProcessor, OutboxSweeper (OutboxWorker
│                                             # removed R18; publisher/listener removed R21)
└── src/test/java/minhdoswe/tickgeek/
    ├── module/auth/...                       # service unit tests
    └── integration/                          # one class per endpoint, plus the required
                                              # concurrency, outbox, email dispatch, spoofed-header,
                                              # security matcher, and migration tests (R14)

tickgeek_frontend/
├── proxy.ts                                  # route guard → /sign-in?next=; refreshes the
│                                             # access token and slides tg_refresh (R2)
├── app/
│   ├── sign-in/page.tsx
│   ├── sign-in/code/page.tsx
│   ├── sign-in/name/page.tsx
│   └── dashboard/page.tsx                    # placeholder organizer home page
├── components/auth/
│   ├── EmailForm.tsx                         # "use client"
│   ├── CodeForm.tsx                          # "use client"
│   ├── ResendButton.tsx                      # "use client", countdown
│   ├── NameForm.tsx                          # "use client"
│   └── SignOutButton.tsx                     # "use client"
├── action/authAction.ts                      # Server Actions
└── lib/
    ├── api/client.ts                         # fetch wrapper: API_URL, bearer, X-Forwarded-For
    ├── api/auth.ts                           # typed calls for every endpoint in the contract
    └── auth/
        ├── cookies.ts                        # tg_access / tg_refresh / tg_registration / tg_sign_in
        ├── clientIp.ts                       # one canonical IP from CLIENT_IP_HEADER (R6)
        └── returnTo.ts                       # safe `next` handling (R12)
```

**Structure Decision**: Web application layout as described in the root README:
`tickgeek_backend/` (Spring Boot) and `tickgeek_frontend/` (Next.js). The backend is organized by
domain module under `minhdoswe.tickgeek.module`, continuing the existing `module/auth`,
`module/user` packages. **Precondition**: the existing backend skeleton is currently staged at the
repository root (`pom.xml`, `src/`) and deleted from the working tree. It must be moved into
`tickgeek_backend/` before implementation (first setup task). The stub classes' wrong `package`
declarations get fixed in the same step.

## Complexity Tracking

No constitution violations to justify. Removing RabbitMQ (R21) narrows the stack in use; it needs
no amendment because the constitution lists approved technology, not required technology.
