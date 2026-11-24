# Implementation Plan: Email Code Sign-In: Request a Code

**Branch**: `001-email-code-auth` | **Date**: 2026-11-11, revised 2026-11-19 (trimmed to the
code-request slice; per-source lock removed, R5) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-request-code/spec.md`

## Summary

The user enters an email on the Next.js sign-in page. A Server Action calls
`POST /api/auth/code-requests` on the Spring Boot backend, which normalizes and validates the
email, takes a per-email advisory lock, checks four rate limits, invalidates the email's earlier
live code, saves a new `PENDING` code as an HMAC, and saves an outbox email job in the same
transaction. It never looks up accounts, so the response is the same for every email. Details:
[research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (LTS); TypeScript on Node.js 26 LTS

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Security, Data JPA, Validation, Flyway),
PostgreSQL JDBC driver; Next.js 16.4.6 (App Router, Server Actions), React 19.3.0.
`pom.xml` also declares the REST client and OAuth2 Resource Server starters, which no code
uses yet.

**Storage**: PostgreSQL; schema in Flyway migrations `V1__create_ticket_system.sql`,
`V2__index_outboxes_verification_code_id.sql` and `V3__add_verification_codes_outbox_id.sql`
(R15)

**Testing**: JUnit 5, Mockito, Spring Boot Test + MockMvc, Testcontainers (PostgreSQL),
Awaitility; unit tests run with Surefire, `*IT` classes with Failsafe; JaCoCo 80% line coverage
gate on `mvn verify`; frontend `npm run lint` + `npm run build`

**Target Platform**: Linux server (backend), Node.js server + modern browsers (frontend)

**Project Type**: Web application (separate backend and frontend projects)

**Performance Goals**: The code request responds in < 300 ms p95 at V1 volume

**Constraints**: No identifiable difference between registered and unregistered emails
(FR-004); infrastructure limited to PostgreSQL; no plaintext codes at rest except inside an
unsent outbox payload; the cooldown and per-email limits are exact under concurrency, while the
per-source and system-wide caps may be exceeded by about the number of code requests in flight
at once (R5)

**Scale/Scope**: Hundreds to low thousands of organizers; up to 1,000 code requests per hour
system-wide (configurable cap); 1 endpoint, 1 page

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v2.0.0, Principles I–V and the Technology Stack. The code uses only Spring Boot and
PostgreSQL, so the stack gate passes. RabbitMQ is on the approved stack but not used.

| Principle | Gate | Status |
|---|---|---|
| I. Clean Code | Methods ≤ 30 lines, ≤ 3 params, no magic numbers, no dead code | PASS for methods: limits and durations are config values; multi-value inputs use records, e.g. `CodeRequestContext` passed to `CodeRequestLimit.check(context)`. Open: `CodeRequestService`'s constructor takes 10 injected dependencies, and whether constructor injection counts as a framework-mandated signature is not decided |
| II. SOLID | Controller → service → repository; constructor injection; extend with new classes; small interfaces | PASS. `AuthController` handles HTTP only; `CodeRequestService` holds the logic; each limit is its own `CodeRequestLimit` class; `CodeRequestService` only knows `CodeEmailQueue` for email |
| III. DRY | Each business rule, validation, and constant has a single source of truth | PASS. Every limit value and duration is defined once in configuration; each limit rule lives in one class; the lock namespace lives only in `CodeRequestLockRepository` |
| IV. Frontend Standards | No `any`; Server Components by default; one component per file ≤ 200 lines; API calls only in `lib/api/` | PASS. Only `EmailForm` is a client component; the Server Action calls `lib/api/auth.ts` |
| V. Testing Discipline | Unit tests for services; ≥ 1 integration test per endpoint; ≥ 80% new-code coverage | PASS. `CodeRequestServiceTest` and `CodeRequestLimitsTest` for the logic; `CodeRequestEndpointIT` plus the risky-boundary tests in R14; JaCoCo gate in the build |

## Project Structure

### Documentation (this feature)

```text
specs/001-request-code/
├── spec.md              # What is built
├── plan.md              # This file
├── research.md          # Design decisions
├── data-model.md        # Tables, states, configuration
├── quickstart.md        # How to validate end to end
├── contracts/
│   └── auth-api.md      # REST endpoint, errors, frontend route
├── tasks.md             # Task list
├── brief-spec.md        # Short review version of spec.md
├── brief-design.md      # Short review version of the design docs
└── checklists/
    └── requirements.md  # Spec quality checklist
```

### Source Code (repository root)

```text
tickgeek_backend/
├── pom.xml
├── src/main/resources/
│   ├── application.yaml                      # datasource, trusted proxies, tickgeek.auth.*
│   ├── application-local.yaml                # dev-only secret, relaxed limits
│   └── db/migration/
│       ├── V1__create_ticket_system.sql      # verification_codes, outboxes
│       ├── V2__index_outboxes_verification_code_id.sql  # FK index (R15)
│       └── V3__add_verification_codes_outbox_id.sql     # code → outbox link, release 1 of 2 (R15)
├── src/main/java/minhdoswe/tickgeek/
│   ├── TickgeekApplication.java
│   ├── common/
│   │   ├── ApiException.java                 # status + error code (+ optional Retry-After)
│   │   ├── EmailAddress.java                 # normalize: strip + lowercase
│   │   ├── ErrorResponse.java                # the one error body shape
│   │   ├── GlobalExceptionHandler.java
│   │   └── RequestSourceResolver.java        # client IP or "unknown" (R6)
│   ├── config/
│   │   ├── AuthProperties.java               # tickgeek.auth; rejects a short secret
│   │   ├── ClockConfig.java                  # injectable Clock
│   │   └── SecurityConfig.java               # one public endpoint, all else denied (R17)
│   └── module/
│       ├── auth/
│       │   ├── AuthController.java           # POST /api/auth/code-requests
│       │   ├── dto/                          # CodeRequestBody, CodeRequestResponse
│       │   ├── code/                         # CodeRequestService, CodeRequestLockRepository,
│       │   │                                 # VerificationCode(+Repository), CodeStatus,
│       │   │                                 # CodeGenerator, CodeHasher, TooManyRequestsException
│       │   └── limit/                        # CodeRequestLimit, CodeRequestContext,
│       │                                     # LimitViolation, RetryAfter, ResendCooldownLimit,
│       │                                     # HourlyLimit + PerEmail/PerSource/GlobalHourlyLimit
│       └── email/                            # CodeEmailQueue, CodeEmailComposer, CodeEmailPayload,
│                                             # Outbox(+Repository), OutboxStatus, OutboxJobScheduled
└── src/test/java/minhdoswe/tickgeek/
    ├── common/, config/, module/...          # unit tests
    ├── integration/                          # *IT classes (R14)
    └── support/                              # AbstractIntegrationTest, TestPostgres,
                                              # MutableClock, IntegrationTestConfig, TestProperties

tickgeek_frontend/
├── app/
│   ├── page.tsx                              # redirects to /sign-in
│   └── sign-in/page.tsx
├── components/auth/
│   ├── EmailForm.tsx                         # "use client"
│   └── FieldError.tsx
├── action/
│   ├── authAction.ts                         # requestCodeAction (Server Action)
│   └── formState.ts                          # form state + error mapping
└── lib/
    ├── api/client.ts                         # fetch wrapper: API_URL, client IP + frontend secret, ApiError
    ├── api/auth.ts                           # requestCode
    ├── api/types.ts                          # contract types
    └── auth/clientIp.ts                      # one IP from CLIENT_IP_HEADER (R6)
```

**Structure Decision**: Web application layout: `tickgeek_backend/` (Spring Boot) and
`tickgeek_frontend/` (Next.js). The backend is organized by domain module under
`minhdoswe.tickgeek.module`.

## Complexity Tracking

No constitution violations to justify. One open question is recorded under Principle I.
