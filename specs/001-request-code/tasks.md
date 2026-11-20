---

description: "Task list for Email Code Sign-In: Request a Code"
---

# Tasks: Email Code Sign-In: Request a Code

**Input**: Design documents from `/specs/001-request-code/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/auth-api.md](contracts/auth-api.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Constitution Principle V requires service unit tests, at least one
integration test per endpoint, and at least 80% line coverage on new code. research.md R14 lists
the risky-boundary integration tests that are mandatory.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: User story from spec.md (US1, US2)

## Path Conventions

- `BE/` = `tickgeek_backend/src/main/java/minhdoswe/tickgeek/`
- `BR/` = `tickgeek_backend/src/main/resources/`
- `BT/` = `tickgeek_backend/src/test/java/minhdoswe/tickgeek/`
- `FE/` = `tickgeek_frontend/`

---

## Phase 1: Setup

- [X] T001 Backend project in `tickgeek_backend/`: Spring Boot 4.1.1 on Java 25 with Web MVC,
  Security, Data JPA, Validation, Flyway (+ `flyway-database-postgresql`) and the PostgreSQL
  driver; test scope `spring-boot-starter-test`, Testcontainers PostgreSQL and Awaitility;
  Failsafe for `*IT` classes; JaCoCo `check` on `verify` requiring 80% line coverage.
- [X] T002 [P] Frontend project in `tickgeek_frontend/`: Next.js 16.4.x, React 19, TypeScript
  `strict`, ESLint with `no-explicit-any` as an error, App Router, import alias `@/*`;
  `FE/.env.example` with `API_URL` and `CLIENT_IP_HEADER`.

---

## Phase 2: Foundational

- [X] T003 `BR/application.yaml`: datasource from `TICKGEEK_DB_*`, `ddl-auto: validate`,
  `open-in-view: false`, `forward-headers-strategy: native`, trusted proxies from
  `TICKGEEK_TRUSTED_PROXIES`, and `tickgeek.auth` (`code-ttl`, `limits.*`, `code-secret`).
  `BR/application-local.yaml` with a dev-only secret and relaxed limits.
- [X] T004 [P] `BE/config/AuthProperties.java` (`@ConfigurationProperties("tickgeek.auth")`,
  nested `Limits`; rejects a secret under 32 bytes) and `BE/config/ClockConfig.java`.
- [X] T005 `BR/db/migration/V1__create_ticket_system.sql`: `outbox_status` enum,
  `verification_codes`, `outboxes` and their indexes, as in data-model.md.
- [X] T006 [P] `BE/common/`: `ErrorResponse`, `ApiException`, `GlobalExceptionHandler`
  (validation and unreadable JSON → 400 `VALIDATION_FAILED`; `ApiException` → its status, code
  and optional `Retry-After`), `EmailAddress`, `RequestSourceResolver`.
- [X] T007 `BE/config/SecurityConfig.java`: stateless, CSRF off, only
  `POST /api/auth/code-requests` permitted, everything else denied (R17).
- [X] T008 [P] Test support in `BT/support/`: `AbstractIntegrationTest` (Spring context,
  MockMvc, shared `TestPostgres` container, test secret, truncate + clock reset before each
  test), `MutableClock`, `IntegrationTestConfig`, `TestProperties`.
- [X] T009 [P] `FE/lib/auth/clientIp.ts`, `FE/lib/api/client.ts` (`apiFetch`, `ApiError`,
  `X-Forwarded-For` = `clientIp()`), `FE/lib/api/types.ts`.

---

## Phase 3: User Story 1 - Request a sign-in code (Priority: P1)

- [X] T010 [P] [US1] Tests: `BT/common/EmailAddressTest.java`,
  `BT/module/auth/code/CodeHasherTest.java`, `BT/module/email/CodeEmailComposerTest.java`,
  `BT/module/email/CodeEmailQueueTest.java`, `BT/config/AuthPropertiesTest.java`.
- [X] T011 [P] [US1] Tests: `BT/module/auth/code/CodeRequestServiceTest.java` and
  `BT/integration/CodeRequestEndpointIT.java` (202 with the contract body, normalization, 400
  cases), `BT/integration/SecurityMatchersIT.java`, `BT/integration/ApplicationContextIT.java`,
  `BT/integration/TrustedProxyForwardingIT.java`,
  `BT/integration/UntrustedProxyForwardingIT.java`.
- [X] T012 [P] [US1] `BE/module/auth/code/`: `CodeStatus`, `VerificationCode`,
  `VerificationCodeRepository`, `CodeGenerator`, `CodeHasher`.
- [X] T013 [P] [US1] `BE/module/email/`: `OutboxStatus`, `Outbox`, `OutboxRepository`,
  `CodeEmailPayload`, `CodeEmailComposer`, `OutboxJobScheduled`, `CodeEmailQueue` (R9).
- [X] T014 [US1] `BE/module/auth/code/CodeRequestLockRepository.java` (per-email advisory lock)
  and `CodeRequestService.java`: normalize → lock email → read `now` → limits → invalidate live
  code → save `PENDING` code → queue the email job → response (R4, R5, R9).
- [X] T015 [US1] `BE/module/auth/dto/CodeRequestBody.java`, `CodeRequestResponse.java` and
  `BE/module/auth/AuthController.java` (`POST /api/auth/code-requests` → 202).
- [X] T016 [US1] Frontend: `FE/lib/api/auth.ts` (`requestCode`), `FE/action/authAction.ts`
  (`requestCodeAction`), `FE/action/formState.ts`, `FE/components/auth/EmailForm.tsx`,
  `FE/components/auth/FieldError.tsx`, `FE/app/sign-in/page.tsx`, `FE/app/page.tsx`.

---

## Phase 4: User Story 2 - Limits on code requests (Priority: P1)

- [X] T017 [P] [US2] Tests: `BT/module/auth/limit/CodeRequestLimitsTest.java`,
  `BT/integration/ResendFlowIT.java`, `BT/integration/CodeRequestConcurrencyIT.java`,
  `BT/integration/GlobalLimitConcurrencyIT.java`.
- [X] T018 [US2] `BE/module/auth/limit/`: `CodeRequestLimit`, `CodeRequestContext`,
  `LimitViolation`, `RetryAfter`, `ResendCooldownLimit` (runs first, discloses the wait),
  `HourlyLimit` and `PerEmailHourlyLimit`, `PerSourceHourlyLimit`, `GlobalHourlyLimit`.
- [X] T019 [US2] `BE/module/auth/code/TooManyRequestsException.java`: cooldown message with
  `Retry-After`; generic message without it for every other limit.

---

## Phase 5: Open

- [X] T020 [US2] Update `BT/integration/CodeRequestConcurrencyIT.java` to the revised R5: the
  per-source lock was removed, so `oneSourceGetsExactlyTwentyAccepted` (renamed
  `oneSourceExceedsItsCapByAtMostTheParallelThreads`) must expect at least 20 and at most 20 plus the number of parallel threads, like
  `GlobalLimitConcurrencyIT`. Also fix the class Javadoc, which still says "per-source and
  per-email advisory locks".
- [X] T021 Qualify every spec reference in code comments in `BE/`, `BT/`, `BR/` and `FE/` with
  the full file path, e.g. `(specs/001-request-code/research.md R5)`, and remap IDs that no
  longer exist (R21 → R9; drop FR-012; User Story 3 → User Story 2).
- [ ] T022 Run `cd tickgeek_backend && ./mvnw verify` and the manual scenarios in
  [quickstart.md](quickstart.md).

---

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2. Within each story, tests first.
- T020 and T021 are independent; T022 runs last.
