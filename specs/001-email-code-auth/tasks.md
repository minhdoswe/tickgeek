---

description: "Task list for Email Code Sign-In and Registration"
---

# Tasks: Email Code Sign-In and Registration

**Input**: Design documents from `/specs/001-email-code-auth/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/auth-api.md](contracts/auth-api.md),
[quickstart.md](quickstart.md)

**Tests**: Required. Constitution Principle V requires service unit tests, at least one
integration test per endpoint, and at least 80% line coverage on new code. research.md R14 lists
risky-boundary integration tests that are mandatory. Write each story's tests first and confirm
they fail before implementing.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: User story from spec.md (US1, US2, US3)

## Path Conventions

Web application (plan.md "Project Structure"). Abbreviations used in task descriptions:

- `BE/` = `tickgeek_backend/src/main/java/minhdoswe/tickgeek/`
- `BR/` = `tickgeek_backend/src/main/resources/`
- `BT/` = `tickgeek_backend/src/test/java/minhdoswe/tickgeek/`
- `FE/` = `tickgeek_frontend/`

Rules for every task (constitution I–IV):
- Methods are ≤ 30 lines with ≤ 3 parameters; use a record when more values are needed.
- No magic numbers: durations and limits come from `AuthProperties`/`EmailProperties`.
- Constructor injection only; controllers handle HTTP only.
- No `switch`/`if` chains on types.
- No dead or commented-out code.
- Frontend: no `any`, one component per file of ≤ 200 lines, backend calls only in
  `FE/lib/api/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Get both projects into the layout plan.md expects.

- [X] T001 Restore the staged backend skeleton from the git index and move it into
  `tickgeek_backend/`. Run `git checkout -- pom.xml mvnw mvnw.cmd src`, then
  `git mv pom.xml mvnw mvnw.cmd src tickgeek_backend/`, and remove the empty staged
  `tickgeek_backend/src/main/resources/db/migration/V2__user_pending_registration.sql`. Result:
  `tickgeek_backend/pom.xml`, `tickgeek_backend/mvnw`,
  `BR/db/migration/V1__create_ticket_system.sql` (unchanged) and `BE/TickgeekApplication.java`
  exist.
- [X] T002 Fix the package declarations of the moved stubs so they match their folders
  (`minhdoswe.tickgeek.module.user`, `minhdoswe.tickgeek.module.auth`,
  `minhdoswe.tickgeek.common`, `minhdoswe.tickgeek.module.event`,
  `minhdoswe.tickgeek.module.organization`). Delete the password-era stubs `BE/module/auth/RegisterRequest.java`,
  `RegisterResponse.java`, `EmailAlreadyExists.java`, `AuthService.java`, `AuthController.java`,
  `VerificationCode.java` and `VerificationCodeRepository.java`; they are replaced in later tasks.
  `cd tickgeek_backend && ./mvnw -q compile` must succeed.
- [X] T003 Update `tickgeek_backend/pom.xml`:
  - Add the Spring Boot 4.1.x starters for Web MVC, Security, OAuth2 Resource Server, Data
    JPA, Validation and Flyway (plus `flyway-database-postgresql`), and the `postgresql` driver
    at runtime scope. Check the exact artifact names against the Boot 4.1 docs.
  - Test scope: `spring-boot-starter-test`, `spring-boot-testcontainers` and the Testcontainers
    PostgreSQL module.
  - Add `jacoco-maven-plugin` with `prepare-agent`, `report` and a `check` goal bound to
    `verify` that requires `LINE` `COVEREDRATIO` ≥ `0.80`.
  - Fill in `<name>` and `<description>`.
- [X] T004 [P] Create the Next.js app in `tickgeek_frontend/`: Next.js 16.4.x, React 19,
  TypeScript `strict: true`, Tailwind CSS 4, ESLint, App Router, no `src/` dir, import alias
  `@/*`. Add the rule `"@typescript-eslint/no-explicit-any": "error"` to `FE/eslint.config.mjs`.
  Delete the empty staged `FE/action/authAction.ts`; it is recreated in T053.
- [X] T005 [P] Create `FE/.env.example` with `API_URL=http://localhost:8080/api` and
  `CLIENT_IP_HEADER=x-real-ip`, each with a comment explaining it (research R6: the hosting
  proxy must overwrite this header, not append to it).
- [X] T006 [P] Create the root `.gitignore` with `.idea/`, `.DS_Store`, `target/`,
  `node_modules/`, `.next/`, `.env.local`, `.env`. Untrack committed build and OS files with
  `git rm -r --cached .DS_Store target`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, configuration, security wiring and shared services that every story uses.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T007 Write `BR/application.yaml`:
  - Datasource: `jdbc:postgresql://localhost:5432/tickgeek`, user `tickgeek`, password
    `tickgeek_password`, each overridable by environment variable.
  - `spring.jpa.hibernate.ddl-auto: validate` and `spring.jpa.open-in-view: false`.
  - `server.forward-headers-strategy: native` and
    `server.tomcat.remoteip.internal-proxies: ${TICKGEEK_TRUSTED_PROXIES:127\.0\.0\.1|0:0:0:0:0:0:0:1}`.
  - `tickgeek.auth`:
    - `code-ttl: 10m`, `max-wrong-attempts: 5`
    - `limits.resend-cooldown: 60s`, `limits.per-email-per-hour: 5`,
      `limits.per-source-per-hour: 20`, `limits.global-per-hour: 1000`
    - `session-idle-timeout: 30d`, `access-token-ttl: 15m`
    - `code-secret: ${TICKGEEK_AUTH_CODE_SECRET}`, `jwt-secret: ${TICKGEEK_JWT_SECRET}`
  - `tickgeek.email`: `max-attempts: 5`, `brevo-api-key: ${BREVO_API_KEY:}`,
    `sender-address`, `sender-name: TickGeek`, `poll-interval: 5s`.
  - Also create `BR/application-local.yaml`, which enables the logging email sender.
- [X] T008 [P] Create `BE/config/AuthProperties.java` as a `@ConfigurationProperties("tickgeek.auth")`
  record with a nested `Limits` record, and `BE/config/EmailProperties.java` for
  `tickgeek.email`. Use `Duration` for durations. Validate at startup that `codeSecret` and
  `jwtSecret` are each ≥ 32 bytes. Enable both with
  `@ConfigurationPropertiesScan` on `BE/TickgeekApplication.java`.
- [X] T009 [P] Create `BE/config/ClockConfig.java` exposing a `Clock` bean (`Clock.systemUTC()`).
  All time-dependent code takes the `Clock` by injection.
- [X] T010 Write `BR/db/migration/V2__email_code_auth.sql`, following data-model.md exactly, in
  this order:
  1. **users**:
     - A `DO` block that raises an exception listing every `lower(btrim(email))` value shared
       by more than one row.
     - `UPDATE users SET email = lower(btrim(email))`.
     - Add `CHECK (email = lower(btrim(email)))`.
     - Add `created_at timestamptz NOT NULL DEFAULT now()` and nullable
       `email_verified_at timestamptz`.
     - Drop `password`.
  2. **verification_codes**: drop the V1 table and recreate it with:
     - `id bigint identity PK`, `email text NOT NULL`, `code_hmac text NOT NULL`
     - `status text NOT NULL CHECK in ('PENDING','ACTIVE','USED','INVALIDATED')`
     - `attempt_count integer NOT NULL DEFAULT 0 CHECK 0–5`
     - `request_source text NOT NULL`, `created_at timestamptz NOT NULL DEFAULT now()`
     - `sent_at`, `expires_at`, `used_at` (all nullable)
     - `CHECK (status <> 'ACTIVE' OR expires_at IS NOT NULL)`
     - Partial `UNIQUE (email) WHERE status IN ('PENDING','ACTIVE')`
     - Indexes `(email, created_at)`, `(request_source, created_at)`, `(created_at)`
  3. **registration_permissions**: `id`, `email text NOT NULL`, `token_hash text NOT NULL UNIQUE`,
     `created_at`, `expires_at timestamptz NOT NULL`, `used_at`.
  4. **refresh_tokens**: `id`, `user_id integer NOT NULL REFERENCES users(id)`,
     `token_hash text NOT NULL UNIQUE`, `created_at`, `last_used_at timestamptz NOT NULL`,
     `revoked_at`; index `(user_id)`.
  5. **outboxes**:
     - `ALTER TYPE outbox_status ADD VALUE 'CANCELLED'`
     - `payload` becomes nullable
     - Add `verification_code_id bigint REFERENCES verification_codes(id)`
- [X] T011 Create `BT/support/AbstractIntegrationTest.java`:
  - `@SpringBootTest`, `@AutoConfigureMockMvc`, a shared PostgreSQL Testcontainer with
    `@ServiceConnection`, and test secrets set via `@DynamicPropertySource`.
  - A `@TestConfiguration` that replaces `Clock` with `BT/support/MutableClock.java` (settable,
    advanceable) and `EmailSender` with `BT/support/RecordingEmailSender.java` (records
    messages, can be told to fail).
  - Table truncation between tests.
- [X] T012 [P] (Removed 2026-11-16: V2 was merged into V1 per R15, so `MigrationIT` was deleted
  and `ApplicationContextIT` expects schema version 1.) Write `BT/integration/MigrationIT.java` (research R14 "Migration") using Flyway
  against a fresh Testcontainer:
  - Migrate to V1 and insert users `Ana@X.com` and ` ana@x.com`. Migrating to V2 fails with an
    error naming `ana@x.com`.
  - On a fresh database, migrate to V1, insert ` Bob@X.com `, then migrate to V2. The email
    becomes `bob@x.com`, `email_verified_at` is NULL and `created_at` is set.
- [X] T013 [P] Create `BE/common/ErrorResponse.java` (record `error`, `message`, nullable
  `fields`), plus `BE/common/ApiException.java`: an abstract runtime exception carrying
  `HttpStatus` and an `error` code string, with an optional `retryAfterSeconds`. Rewrite
  `BE/common/GlobalExceptionHandler.java`:
  - `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` with `fields`.
  - `ApiException` → its own status and code, plus a `Retry-After` header when set. Use no type
    switches.
- [X] T014 [P] Create `BE/common/TokenGenerator.java` with `newToken()` (256-bit `SecureRandom`,
  base64url without padding) and `sha256Hex(String)`. Add unit test
  `BT/common/TokenGeneratorTest.java`.
- [X] T015 [P] Create `BE/module/user/User.java` (JPA entity for `users`: id, email, firstName,
  lastName, emailVerifiedAt, createdAt; no password). Create `BE/module/user/UserRepository.java`
  with `findByEmail`, and a native `insertIfAbsent(email, firstName, lastName, now)` using
  `INSERT ... ON CONFLICT (email) DO NOTHING` (research R8). Take a record parameter if more
  than 3 values are needed.
- [X] T016 *(Removed 2026-11-17: security events are no longer recorded.)*
- [X] T017 [P] Create the `BE/module/email/` persistence classes:
  - `OutboxStatus.java` (PENDING, SENT, ERROR, CANCELLED).
  - `Outbox.java`, an entity for `outboxes` that includes `verificationCodeId`.
  - `OutboxRepository.java` with a native `claimDue(limit)` query:
    `status='PENDING' AND next_attempt_at <= now()`, ordered by `created_at`,
    `FOR UPDATE SKIP LOCKED`.
- [X] T018 [P] Create `BE/module/email/EmailSender.java` (one method,
  `send(EmailMessage message)`) and `EmailMessage.java` (record: to, subject, textBody).
  Implement two senders:
  - `BrevoEmailSender.java`: Spring `RestClient` `POST https://api.brevo.com/v3/smtp/email`
    with the `api-key` header from `EmailProperties`. Throw `EmailDeliveryException` on a
    non-2xx response.
  - `LoggingEmailSender.java` (`@Profile("local")`, `@Primary`): logs the message.
  Add `BT/module/email/BrevoEmailSenderTest.java` using `MockRestServiceServer` to check the
  request body and headers, and that an error status throws.
- [X] T019 [P] Create `BE/config/JwtConfig.java`: an HS256 `SecretKey` from
  `AuthProperties.jwtSecret()`, a `NimbusJwtEncoder`, and a `NimbusJwtDecoder.withSecretKey`
  with a validator requiring issuer `tickgeek` plus the default timestamp validation.
- [X] T020 Create `BE/config/SecurityConfig.java` (research R17):
  - `SessionCreationPolicy.STATELESS`; CSRF disabled (bearer tokens only, no backend cookies).
  - `permitAll` for exactly `POST /api/auth/code-requests`, `/code-verifications`,
    `/registrations`, `/token-refreshes` and `/sign-out`.
  - `authenticated` for `/api/**`, using `oauth2ResourceServer(jwt)`; `denyAll` for anything
    else.
  - The authentication entry point writes `ErrorResponse("UNAUTHENTICATED", ...)` with
    status 401.
- [X] T021 [P] Create `BE/common/RequestSourceResolver.java`. It returns
  `request.getRemoteAddr()`, which Tomcat's RemoteIpValve has already rewritten only for
  trusted proxies, or `"unknown"` when blank.
- [X] T022 [P] Create `FE/lib/api/types.ts` with the contract types: `TokenPair`,
  `CodeRequestResponse`, `VerificationResponse` (a discriminated union on `outcome`), `Me`,
  `ApiErrorCode` (a union of every `error` value in contracts/auth-api.md) and `ApiErrorBody`.
- [X] T023 [P] Create `FE/lib/auth/clientIp.ts`. `clientIp()` reads only the header named by
  `process.env.CLIENT_IP_HEADER` via `headers()`. It returns it only if it is a valid IPv4/IPv6
  address, otherwise `"unknown"`, and never reads `x-forwarded-for` from the incoming request
  (research R6).
- [X] T024 Create `FE/lib/api/client.ts`. `apiFetch<T>(path, { method, body, accessToken })`
  prefixes `process.env.API_URL` and sends JSON. It sets `X-Forwarded-For` to exactly
  `clientIp()`, and sets `Authorization: Bearer` when given. It throws a typed `ApiError`
  (status, code, message, fields, retryAfterSeconds) on non-2xx responses and contains no `any`.
- [X] T025 [P] Create `FE/lib/auth/cookies.ts` with get/set/clear helpers for `tg_access`,
  `tg_refresh` (30-day `Max-Age`), `tg_registration` (expires at `registrationExpiresAt`) and
  `tg_sign_in` (JSON `{ email, next }`, 10 minutes). All cookies are `HttpOnly`,
  `SameSite=Lax` and `Path=/`, with `Secure` unless `NODE_ENV === "development"`. Add
  `setTokenPair(pair)` and `clearSession()`.
- [X] T026 [P] Create `FE/lib/auth/returnTo.ts`. `safeNext(value)` returns `value` only if it
  starts with `/` and not with `//` or `/\`, otherwise `"/dashboard"` (research R12).

**Checkpoint**: The backend boots against PostgreSQL with V1 (full schema) applied; the
frontend builds.

---

## Phase 3: User Story 1 - Register a new account with an email code (Priority: P1) 🎯 MVP

**Goal**: A new user enters an email, receives a code, enters it, gives a first and last name,
and is signed in on `/dashboard`.

**Independent Test**: Using an unregistered email, complete `/sign-in` → `/sign-in/code` →
`/sign-in/name`. You land on `/dashboard` showing your name, and `users` has exactly one row
with `email_verified_at` set (quickstart scenario 1).

### Tests for User Story 1 ⚠️ (write first, confirm they fail)

- [X] T027 [P] [US1] `BT/module/auth/limit/CodeRequestLimitsTest.java`: unit-test each of the
  4 limit classes at boundary values. The cooldown blocks at 59 s and allows at 60 s; the
  per-email limit blocks the 6th request in an hour; the per-source limit blocks the 21st; the
  global limit blocks the 1,001st. Check the retry-after values too.
- [X] T028 [P] [US1] `BT/module/auth/code/CodeRequestServiceTest.java`: normalizes the email.
  It invalidates earlier PENDING/ACTIVE codes, inserts a PENDING code with an HMAC (never the
  plaintext) and writes an outbox row linked by `verificationCodeId`. When a limit fails it
  throws `TooManyRequestsException`. It never touches `UserRepository`.
- [X] T029 [P] [US1] `BT/module/auth/code/CodeVerificationServiceTest.java`:
  - A correct code for an unknown email → `RegistrationRequired`; the code becomes `USED`, a
    permission is created with the code's `expires_at`.
  - A wrong code increments the attempt count → `CodeInvalidException` with attempts left.
  - The 5th wrong code → `INVALIDATED` and `CodeLockedException`.
  - An expired, PENDING or missing code → `CodeExpiredException`.
- [X] T030 [P] [US1] `BT/module/auth/registration/RegistrationServiceTest.java`: a valid
  permission is used up, the user is inserted (or the existing one loaded), and a `TokenPair`
  is returned. A
  missing, used or expired permission → `RegistrationExpiredException`.
- [X] T031 [P] [US1] `BT/module/auth/token/AccessTokenIssuerTest.java`: the JWT has
  `iss=tickgeek`, `sub=<userId>`, `iat`, and `exp = now + 15m` from the clock.
- [X] T032 [P] [US1] `BT/integration/CodeRequestEndpointIT.java`:
  - 202 with `{email, codeValidMinutes, resendAvailableAt}`, and a byte-identical body shape
    and status for a registered and an unregistered email.
  - 400 `VALIDATION_FAILED` for a malformed email.
  - 429 with `Retry-After` for the cooldown and the per-email hourly limit.
  - Spoofed `X-Forwarded-For` from an untrusted remote address (MockMvc `remoteAddr`
    `10.9.9.9`) stores `request_source` as `10.9.9.9`. From a trusted proxy remote address it
    stores the forwarded IP.
- [X] T033 [P] [US1] `BT/integration/CodeRequestConcurrencyIT.java` (parallel threads):
  - Two requests for one email give exactly one PENDING/ACTIVE row.
  - 30 requests for different emails from one source give exactly 20 accepted.
  - With `global-per-hour` set to 10, 30 requests from different sources give exactly 10
    accepted.
- [X] T034 [P] [US1] `BT/integration/OutboxWorkerIT.java`:
  - Run the worker once → `RecordingEmailSender` received the code. The code becomes `ACTIVE`
    with `expires_at = sent_at + 10m`, the job is `SENT` and the payload is NULL.
  - Code replaced before the worker runs → the job is `CANCELLED` with a NULL payload and
    nothing is sent.
  - Code replaced after the send but before activation → the code stays `INVALIDATED`.
  - 5 sender failures → the job is `ERROR` with a NULL payload and the code is `INVALIDATED`.
- [X] T035 [P] [US1] `BT/integration/CodeVerificationEndpointIT.java`:
  - 200 `REGISTRATION_REQUIRED` with `registrationToken` and `registrationExpiresAt`.
  - 422 `CODE_INVALID` with attempts left.
  - The 5th wrong attempt → 410 `CODE_LOCKED`.
  - Expired after a `MutableClock` advance of 10m → 410 `CODE_EXPIRED`.
  - Code `12a456` → 400 with no attempt counted.
  - 10 parallel wrong guesses evaluate at most 5 wrong attempts.
  - 2 parallel correct verifications give exactly one 200.
- [X] T036 [P] [US1] `BT/integration/RegistrationEndpointIT.java`:
  - 200 with a token pair; the `users` row has a trimmed name and `email_verified_at` set.
  - 400 for an empty or 51-character name.
  - 410 for a used permission, and for an expired one (clock advanced).
  - Two permissions for one email submitted in parallel give one user, both get 200 token
    pairs, and the first name processed is kept.
- [X] T037 [P] [US1] `BT/integration/MeEndpointIT.java`: 200 `{id,email,firstName,lastName}`
  with a valid JWT. 401 `UNAUTHENTICATED` with no token, an expired token (clock advanced
  16m), a token whose signature was edited, or a JWT signed with the right key but issuer
  `other`.

### Implementation for User Story 1

- [X] T038 [P] [US1] Create `BE/module/auth/code/CodeStatus.java` (PENDING, ACTIVE, USED,
  INVALIDATED), `VerificationCode.java` (entity per data-model) and
  `VerificationCodeRepository.java` with:
  - `findActiveForUpdate(email)` (`SELECT ... FOR UPDATE`, status `ACTIVE`).
  - `invalidateLive(email)` (PENDING/ACTIVE → INVALIDATED).
  - `countByEmailSince`, `countBySourceSince`, `countSince` and `findLatestCreatedAt(email)`.
  - Native `activateIfPending(id, sentAt, expiresAt)` returning the updated row count.
- [X] T039 [P] [US1] Create `BE/module/auth/code/CodeGenerator.java` (`SecureRandom`, 6 digits,
  zero-padded) and `CodeHasher.java` (`HmacSHA256` over `email + ":" + code` with
  `AuthProperties.codeSecret()`; `matches()` uses `MessageDigest.isEqual`). Add unit test
  `BT/module/auth/code/CodeHasherTest.java`.
- [X] T040 [P] [US1] Create `BE/module/auth/registration/RegistrationPermission.java` and
  `RegistrationPermissionRepository.java` with a native `consume(tokenHash, now)`:
  `UPDATE ... SET used_at = now WHERE token_hash = ? AND used_at IS NULL AND expires_at > now RETURNING email`.
- [X] T041 [P] [US1] Create `BE/module/auth/token/RefreshToken.java` and
  `RefreshTokenRepository.java` for `refresh_tokens`.
- [X] T042 [US1] Create `BE/module/auth/limit/CodeRequestLimit.java` with
  `Optional<LimitViolation> check(CodeRequestContext ctx)`. `CodeRequestContext` is a record
  of email, source and now; `LimitViolation` is a record of limitName and retryAfterSeconds.
  Implement 4 classes in the same folder: `ResendCooldownLimit`, `PerEmailHourlyLimit`,
  `PerSourceHourlyLimit` and `GlobalHourlyLimit`. All are injected as `List<CodeRequestLimit>`.
- [X] T043 [US1] Create `BE/module/auth/code/CodeRequestService.java`
  (`request(email, source)` → `CodeRequestResult`). It runs in one transaction:
  1. `SELECT pg_advisory_xact_lock(CODE_REQUEST_LOCK_KEY)`, with the key as a named constant.
     (Superseded 2026-11-16 by Phase 8: a per-source lock, then a per-email lock; R5.)
  2. Run every limit. On a violation, throw `TooManyRequestsException`. The message is generic except for the cooldown, which
     states the seconds to wait.
  3. `invalidateLive`, then insert a PENDING code.
  4. Insert an outbox row whose payload is JSON `{to, code}`, with `verificationCodeId` set.
- [X] T044 [US1] Create `BE/module/email/CodeEmailComposer.java`, which builds an English
  `EmailMessage` with the code, the validity in minutes from config, and the "ignore if you
  didn't request this" note (FR-016). Create `BE/module/email/OutboxWorker.java`, an
  `@Scheduled(fixedDelayString = "${tickgeek.email.poll-interval}")` job, following research R9:
  - Claim due jobs and skip any whose code is not PENDING (set `CANCELLED`, payload NULL).
  - Send the email. On success, in one transaction, set the job to `SENT`, erase the payload
    and call `activateIfPending`.
  - On failure, increment attempts and set `next_attempt_at` with exponential backoff. At
    `max-attempts`, set the job to `ERROR`, erase the payload and invalidate the code.
  Add `@EnableScheduling` to `BE/TickgeekApplication.java`.
- [X] T045 [US1] Create `BE/module/auth/token/TokenPair.java` (record), `AccessTokenIssuer.java`
  (JWT via `JwtEncoder`, HS256 header, claims per contract) and `RefreshTokenService.java`.
  `RefreshTokenService.issue(userId)` generates a token, stores its SHA-256 with
  `last_used_at = now`, and returns a `TokenPair` with the access token.
- [X] T046 [US1] Create the `BE/module/auth/code/` exception classes extending `ApiException`:
  `CodeInvalidException` (422 `CODE_INVALID`, with attempts left), `CodeLockedException`
  (410), `CodeExpiredException` (410), `TooManyRequestsException` (429 with retry-after) and
  `BE/module/auth/registration/RegistrationExpiredException.java` (410 `REGISTRATION_EXPIRED`).
- [X] T047 [US1] Create `BE/module/auth/code/VerificationOutcome.java`, a sealed interface with
  `toResponse()`. Implement `RegistrationRequired` (registrationToken, expiresAt) now; `SignedIn`
  is added in T064. Create `CodeVerificationService.java`
  (`verify(email, code, source)`), following research R4:
  - Run in one transaction and call `findActiveForUpdate`.
  - Expired → `CodeExpiredException`.
  - Match → mark `USED`. If no user exists, issue a registration
    permission (`TokenGenerator`, `expires_at` = the code's `expires_at`) → `RegistrationRequired`.
  - No match → increment the attempt count. On the 5th, set
    `INVALIDATED` → `CodeLockedException`; otherwise `CodeInvalidException`.
- [X] T048 [US1] Create `BE/module/auth/registration/RegistrationService.java`
  (`complete(registrationToken, name, source)`, where `name` is a record of first and last
  name). In one transaction, following research R8:
  1. `consume(sha256(token))`; nothing returned → `RegistrationExpiredException`.
  2. `insertIfAbsent`, then `findByEmail`.
  3. `RefreshTokenService.issue`.
- [X] T049 [P] [US1] Create the `BE/module/auth/dto/` records with Bean Validation:
  - `CodeRequestBody` (`@NotBlank @Email @Size(max=254) email`).
  - `CodeVerificationBody` (email, plus `@Pattern("\\d{6}") code`).
  - `RegistrationBody` (`@NotBlank registrationToken`, plus `@NotBlank @Size(max=50)`
    `firstName` and `lastName`, checked after trimming).
  - Response records: `CodeRequestResponse`, `VerificationResponse`, `TokenPairResponse` and
    `MeResponse`, all matching contracts/auth-api.md.
- [X] T050 [US1] Create `BE/module/auth/AuthController.java` (HTTP only). It delegates to the
  services and uses `RequestSourceResolver`, with these endpoints:
  - `POST /api/auth/code-requests` → 202.
  - `POST /api/auth/code-verifications` → 200 via `outcome.toResponse()`.
  - `POST /api/auth/registrations` → 200.
  - `GET /api/auth/me` → 200, reading `sub` from the `Jwt` principal via a `UserService` or
    the repository through a service, never directly.
- [X] T051 [US1] Create `BE/module/user/UserService.java` with `getProfile(userId)` (throws a
  401 `UNAUTHENTICATED` `ApiException` if the user is missing). Add
  `BT/module/user/UserServiceTest.java`.
- [X] T052 [US1] Create `FE/lib/api/auth.ts` with `requestCode(email)`,
  `verifyCode(email, code)`, `completeRegistration(token, firstName, lastName)` and
  `getMe(accessToken)`, all typed with `FE/lib/api/types.ts` and calling `apiFetch` only.
- [X] T053 [US1] Create `FE/action/authAction.ts` (`"use server"`) with three actions:
  - `requestCodeAction`: on success, save `tg_sign_in` and redirect to `/sign-in/code`.
  - `verifyCodeAction`: for `REGISTRATION_REQUIRED`, save `tg_registration` and redirect to
    `/sign-in/name`.
  - `completeRegistrationAction`: on success, call `setTokenPair`, clear `tg_registration` and
    `tg_sign_in`, and redirect to `safeNext(next)`.
  Each returns `{ error, fields }` state for `useActionState` on `ApiError`.
- [X] T054 [P] [US1] Create `FE/components/auth/EmailForm.tsx` (`"use client"`,
  `useActionState(requestCodeAction)`, an email input with a `defaultValue` prop, and field
  errors).
- [X] T055 [P] [US1] Create `FE/components/auth/CodeForm.tsx` (`"use client"`; one 6-digit
  input with `inputMode="numeric"`, `autoComplete="one-time-code"` and `maxLength={6}`; shows
  the error message from state).
- [X] T056 [P] [US1] Create `FE/components/auth/NameForm.tsx` (`"use client"`, first and last
  name inputs with `maxLength={50}`, field errors).
- [X] T057 [US1] Create the server-component pages:
  - `FE/app/sign-in/page.tsx`: renders `EmailForm` and stores `?next` for the action through
    a hidden input.
  - `FE/app/sign-in/code/page.tsx`: reads `tg_sign_in` (missing → redirect to `/sign-in`),
    shows "We sent a code to {email}. It's valid for 10 minutes." and renders `CodeForm`.
  - `FE/app/sign-in/name/page.tsx`: without a `tg_registration` cookie → redirect to
    `/sign-in`; otherwise renders `NameForm`.
- [X] T058 [US1] Create `FE/app/dashboard/page.tsx` (a server component that calls `getMe` with
  `tg_access` and shows "Welcome, {firstName}"). Create `FE/proxy.ts` with matcher
  `/dashboard/:path*`: when neither `tg_access` nor `tg_refresh` is present, redirect to
  `/sign-in?next=<path>`.

**Checkpoint**: US1 is fully functional. Quickstart scenarios 1, 7, 8, 10 (partly), 11, 12 and
13 pass.

---

## Phase 4: User Story 2 - Sign in to an existing account with an email code (Priority: P1)

**Goal**: Returning users sign in with a code (no name step). Sessions slide for 30 days via
token refresh, sign-out ends only the current device, and users return to the page they came
from.

**Independent Test**: With an existing account, sign in using a different-case email and land
on the original protected page. Sign in on a second browser, then sign out in one; the other
stays signed in (quickstart 2, 3, 4, 9, 9b and §4).

**Depends on**: US1 code request and verification (T038–T050). It adds the `SignedIn` outcome.

### Tests for User Story 2 ⚠️ (write first, confirm they fail)

- [X] T059 [P] [US2] Extend `BT/module/auth/code/CodeVerificationServiceTest.java`: a correct
  code for an existing user → `SignedIn` with a `TokenPair`. A legacy
  user with NULL `email_verified_at` has it set to now.
- [X] T060 [P] [US2] `BT/module/auth/token/RefreshTokenServiceTest.java`:
  - `refresh` with a valid token returns a new access token and the same refresh token, and
    sets `last_used_at` to now.
  - Revoked, unknown or idle (> 30d) → `SessionExpiredException`.
  - `revoke` sets `revoked_at` once and is a no-op for unknown or revoked tokens.
- [X] T061 [P] [US2] `BT/integration/SignInExistingUserIT.java`:
  - An existing user verifying with `" ANA@Example.com "` → 200 `SIGNED_IN` with a token pair,
    and `users` still has 1 row.
  - A legacy user seeded with NULL `email_verified_at` becomes verified.
  - Two devices signing in get two `refresh_tokens` rows.
- [X] T062 [P] [US2] `BT/integration/TokenRefreshEndpointIT.java`:
  - 200 with a new `accessToken` and an unchanged `refreshToken`; `last_used_at` moves.
  - 401 `SESSION_EXPIRED` after the `MutableClock` is advanced 30d + 1m.
  - 401 after sign-out.
  - Two parallel refreshes with the same token both return 200.
- [X] T063 [P] [US2] `BT/integration/SignOutEndpointIT.java`:
  - 204 for a valid, unknown or already-revoked token.
  - Only the given refresh token is revoked; the other device's refresh still returns 200.
  - The access token issued before sign-out still gets 200 on `/me` until it expires (the
    accepted trade-off), and 401 after the clock is advanced 16m.

### Implementation for User Story 2

- [X] T064 [US2] Add the `SignedIn` record (holding a `TokenPair`) to
  `BE/module/auth/code/VerificationOutcome.java`. Update `CodeVerificationService.java` so that
  on a match with an existing user it sets `email_verified_at` if NULL and returns `SignedIn(refreshTokenService.issue(userId))`.
- [X] T065 [US2] Add `refresh(refreshToken)` and `revoke(refreshToken, source)` to
  `BE/module/auth/token/RefreshTokenService.java`, following research R2:
  - Refresh is valid when `revoked_at IS NULL AND last_used_at > now - session-idle-timeout`;
    it updates `last_used_at` and returns a new access token with the same refresh token.
  - Revoke sets `revoked_at`.
  Create `BE/module/auth/token/SessionExpiredException.java` (401 `SESSION_EXPIRED`).
- [X] T066 [US2] Add `RefreshTokenBody` (`@NotBlank refreshToken`) to `BE/module/auth/dto/`, and
  two endpoints to `BE/module/auth/AuthController.java`: `POST /api/auth/token-refreshes` → 200
  token pair, and `POST /api/auth/sign-out` → 204.
- [X] T067 [US2] Add `refreshAccessToken(refreshToken)` and `signOut(refreshToken)` to
  `FE/lib/api/auth.ts`.
- [X] T068 [US2] Extend `FE/proxy.ts` (research R2) for protected paths:
  - If `tg_access` is missing or its JWT `exp` (decoded without verification, only to decide
    when to refresh) is within 60 s, and `tg_refresh` exists, call `refreshAccessToken`.
  - On success, set `tg_access` and re-set `tg_refresh` with a fresh 30-day `Max-Age` on the
    response.
  - On `SESSION_EXPIRED`, clear both and redirect to `/sign-in?next=<path>`.
- [X] T069 [US2] Update `FE/lib/api/client.ts` with an `apiFetchAuthed<T>` used from Server
  Actions. On a 401 `UNAUTHENTICATED` it refreshes once via `refreshAccessToken`, updates the
  cookies and retries once. If the refresh fails, it calls `clearSession()` and redirects to
  `/sign-in`.
- [X] T070 [US2] Update `verifyCodeAction` in `FE/action/authAction.ts` to handle `SIGNED_IN`:
  call `setTokenPair`, clear `tg_sign_in`, and redirect to `safeNext(next)` from `tg_sign_in`.
  Add `signOutAction`, which calls `signOut`, then `clearSession()`, and redirects to
  `/sign-in`.
- [X] T071 [P] [US2] Create `FE/components/auth/SignOutButton.tsx` (`"use client"`, a form
  posting to `signOutAction`) and render it in `FE/app/dashboard/page.tsx`.

**Checkpoint**: US1 and US2 both work. Quickstart 2, 3, 4, 9, 9b and §4 pass.

---

## Phase 5: User Story 3 - Recover from a missing or expired code (Priority: P2)

**Goal**: Resend with a visible 60-second countdown, change the email with it prefilled, and
clear messages for expired or locked codes.

**Independent Test**: Request a code, see the resend countdown, resend after 60 s, and confirm
only the newest code works. "Change email" returns to the email screen with the address
prefilled (quickstart 5, 6).

### Tests for User Story 3 ⚠️ (write first, confirm they fail)

- [X] T072 [P] [US3] `BT/integration/ResendFlowIT.java`:
  - A resend within 60 s → 429 whose message states the seconds left, with a `Retry-After`
    header.
  - After the clock is advanced 60 s, a new code is issued, and verifying the old code → 410
    `CODE_EXPIRED`.
  - The 6th request in an hour → 429.
  - A code expired 10m after activation → 410 `CODE_EXPIRED`.
  - Responses are identical for registered and unregistered emails.

### Implementation for User Story 3

- [X] T073 [P] [US3] Create `FE/components/auth/ResendButton.tsx` (`"use client"`). It takes a
  `resendAvailableAt` prop and counts down each second, disabled with "Resend in {n}s" until
  zero, then enabled. It submits to `resendCodeAction`.
- [X] T074 [US3] Add `resendCodeAction` to `FE/action/authAction.ts`. It calls `requestCode` with
  the email from `tg_sign_in`, updates `resendAvailableAt` in `tg_sign_in`, and returns a 429
  error message as state. Also store `resendAvailableAt` in `tg_sign_in` from
  `requestCodeAction`.
- [X] T075 [US3] Update `FE/app/sign-in/code/page.tsx`:
  - Render `ResendButton` with `resendAvailableAt` from `tg_sign_in`, and a "Use a different
    email" link to `/sign-in?change=1`.
  - In `FE/components/auth/CodeForm.tsx`, show `CODE_EXPIRED` / `CODE_LOCKED` errors with the
    text "Send a new code" pointing to the resend action.
- [X] T076 [US3] Update `FE/app/sign-in/page.tsx` to prefill `EmailForm` with the email from
  `tg_sign_in` when it is present (US3 scenario 4).

**Checkpoint**: All three stories work independently.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T077 [P] Create `BE/module/security/AuthDataCleanupJob.java` (a daily `@Scheduled` cron
  from config) that deletes:
  - `refresh_tokens` revoked or idle for more than 90d.
  - `verification_codes` and `registration_permissions` created more than 90d ago.
  Add unit test `BT/module/security/AuthDataCleanupJobTest.java`.
- [X] T078 [P] Replace `tickgeek_backend/docs/api/auth.md` with a summary that links to
  `specs/001-email-code-auth/contracts/auth-api.md`. Add `tickgeek_backend/docs/deployment.md`
  covering the required environment variables (`TICKGEEK_AUTH_CODE_SECRET`,
  `TICKGEEK_JWT_SECRET`, `BREVO_API_KEY`, `TICKGEEK_TRUSTED_PROXIES`, `CLIENT_IP_HEADER`) and
  the rule that the edge proxy must overwrite `CLIENT_IP_HEADER`.
- [X] T079 [P] Update the root `README.md`: passwordless sign-in, the new environment
  variables, versions (Spring Boot 4.1.x, Next.js 16.4.x, Node.js 26 LTS), the
  `./mvnw verify` coverage gate, and `local` profile email logging.
- [X] T080 Constitution compliance pass over the files from T007–T076:
  - Methods ≤ 30 lines with ≤ 3 parameters, no magic numbers, no dead or commented-out code,
    no field `@Autowired`, no type switches.
  - Frontend: no `any` (`npm run lint`), components ≤ 200 lines, backend calls only in
    `FE/lib/api/`.
  Fix any violations.
- [X] T081 Run `cd tickgeek_backend && ./mvnw verify` (all tests pass, JaCoCo ≥ 80%) and
  `cd tickgeek_frontend && npm run lint && npm run build`.
- [ ] T082 Run the manual scenarios in `specs/001-email-code-auth/quickstart.md` with the
  `local` profile and record the results in the PR description.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. T001 → T002 → T003 in order; T004–T006 run in parallel with them.
- **Foundational (Phase 2)**: depends on Setup.
  - T007 → T008 → T010.
  - T011 needs T003, T009 and T018.
  - T020 needs T013 and T019.
  - T024 needs T022 and T023.
- **US1 (Phase 3)**: depends on Foundational.
- **US2 (Phase 4)**: depends on Foundational and the US1 backend core (T038–T050), because it
  extends `CodeVerificationService` and `RefreshTokenService`.
- **US3 (Phase 5)**: depends on US1 (code request and the code page). It is independent of US2.
- **Polish (Phase 6)**: after the stories you want to ship.

### Within Each Story

Tests first, and they must fail. Then entities → hashing/limits → services → controller →
frontend API → actions → components → pages.

- **US1 backend**: T038–T041 [P] → T042 → T043 → T044. T045–T046 → T047 → T048 → T049 → T050–T051.
- **US1 frontend**: T052 → T053 → T054–T056 [P] → T057 → T058.

### Parallel Opportunities

- Setup: T004, T005 and T006 alongside T001–T003.
- Foundational: T008, T009, T012–T019, T021–T023, T025 and T026 touch different files.
- US1: all tests T027–T037; entities T038–T041; components T054–T056.
- US2: tests T059–T063; T071 alongside T067–T070.
- US3: T072 alongside T073; US3 can proceed in parallel with US2 once US1 is done.
- Polish: T077, T078 and T079.

---

## Parallel Example: User Story 1

```bash
# Tests first, all in parallel:
Task: "CodeRequestLimitsTest in BT/module/auth/limit/CodeRequestLimitsTest.java"
Task: "CodeRequestConcurrencyIT in BT/integration/CodeRequestConcurrencyIT.java"
Task: "OutboxWorkerIT in BT/integration/OutboxWorkerIT.java"
Task: "CodeVerificationEndpointIT in BT/integration/CodeVerificationEndpointIT.java"
Task: "RegistrationEndpointIT in BT/integration/RegistrationEndpointIT.java"

# Entities in parallel:
Task: "VerificationCode + repository in BE/module/auth/code/"
Task: "RegistrationPermission + repository in BE/module/auth/registration/"
Task: "RefreshToken + repository in BE/module/auth/token/"

# Frontend forms in parallel:
Task: "EmailForm in FE/components/auth/EmailForm.tsx"
Task: "CodeForm in FE/components/auth/CodeForm.tsx"
Task: "NameForm in FE/components/auth/NameForm.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → Phase 2 Foundational. `ApplicationContextIT` must be green.
2. Phase 3 (US1). New organizers can register and reach `/dashboard`.
3. **Stop and validate**: run quickstart scenario 1 plus the US1 integration tests.

### Incremental Delivery

1. US1: registration (MVP).
2. US2: returning sign-in, token refresh, sign-out, return-to-page. This completes the P1 flow,
   and the feature is shippable here.
3. US3: resend/countdown and change-email polish.
4. Polish: cleanup job, docs, compliance pass, full verify.

---

## Notes

- [P] = different files and no dependency on an incomplete task.
- Commit after each task or logical group, on branch `001-email-code-auth` (create it from
  `master` first).
- Never log or store plaintext codes or tokens, except the code in an unsent outbox payload.
- If a Spring Boot 4.1 starter name in T003 differs, use the documented name. Do not add other
  libraries without updating plan.md.

---

## Phase 7: Convergence

Email delivery via RabbitMQ (plan revised 2026-11-13; research R18–R20,
[contracts/email-messaging.md](contracts/email-messaging.md)). Write each test task before the
code it covers and confirm it fails first.

- [X] T083 Add `spring-boot-starter-amqp`, test-scoped `testcontainers-rabbitmq` and test-scoped `org.awaitility:awaitility` (versions managed by the Spring Boot parent) to `tickgeek_backend/pom.xml` per plan: Primary Dependencies / Testing (missing)
- [X] T084 Update `BR/application.yaml`: add `spring.rabbitmq.host/port/username/password` from `TICKGEEK_RABBITMQ_HOST` (localhost) / `_PORT` (5672) / `_USER` / `_PASSWORD` (guest), `spring.rabbitmq.connection-timeout: 2s`, `spring.rabbitmq.listener.simple.default-requeue-rejected: false`, `tickgeek.email.sweep-interval: 2m`, `tickgeek.email.stuck-after: 2m`; remove `tickgeek.email.poll-interval`; add `sweepInterval` and `stuckAfter` to `BE/config/EmailProperties.java` per data-model: Configuration values (missing)
- [X] T085 Add `CREATE INDEX outboxes_pending_due_idx ON outboxes (next_attempt_at) WHERE status = 'PENDING'` to `BR/db/migration/V1__create_ticket_system.sql` per data-model: outboxes, R20 (missing)
- [X] T086 Create `BE/config/EmailMessagingConfig.java`: declare the exchanges `tickgeek.email` (direct), `tickgeek.email.delay` (direct), `tickgeek.email.dlx` (fanout), the queue `tickgeek.email.code` (dead-letters to `tickgeek.email.dlx`), the queue `tickgeek.email.code.dead`, and one delay queue `tickgeek.email.code.delay.<ms>` per retry (attempt n = 1..max-attempts−1, `<ms>` = retry-base-delay × 2^(n−1), `x-message-ttl` = ms, dead-letter to `tickgeek.email` / `code-email`) with their bindings, plus a JSON `MessageConverter`; keep the names as constants in one place per contracts/email-messaging.md, R19 (missing)
- [X] T087 Create `BE/module/email/OutboxJobScheduled.java` (record: `UUID outboxId`, `Duration delay`) and `BE/module/email/OutboxMessagePublisher.java`: a `@TransactionalEventListener(phase = AFTER_COMMIT)` that sends `{"outboxId": …}` as a persistent message to `tickgeek.email`/`code-email` when the delay is zero, or to `tickgeek.email.delay`/`delay.<ms>` otherwise; it logs and swallows `AmqpException` per R18, R19 (missing)
- [X] T088 Create `BE/module/email/CodeEmailQueue.java` with `enqueue(Long verificationCodeId, String payload, Instant now)`: it saves `Outbox.forCode(...)` and publishes `OutboxJobScheduled(id, Duration.ZERO)` through `ApplicationEventPublisher`. Change `BE/module/auth/code/CodeRequestService.java` to depend on `CodeEmailQueue` in place of `OutboxRepository`, and update `CodeRequestServiceTest` per plan: Constitution II row, R18 (contradicts)
- [X] T089 Rework `BE/module/email/OutboxJobProcessor.java`: replace `processNext()` with `@Transactional process(UUID outboxId)`. It uses a new `OutboxRepository.claimDueById(id, now)` (`SELECT … WHERE id = ? AND status = 'PENDING' AND next_attempt_at <= ? FOR UPDATE SKIP LOCKED`); no row → return without doing anything. On `EmailDeliveryException` with attempts left, publish `OutboxJobScheduled(id, backoff)`. Remove `claimDue` once unused, and update `OutboxJobProcessorTest` per R18, R19 (partial)
- [X] T090 Create `BE/module/email/CodeEmailListener.java`: `@RabbitListener(queues = "tickgeek.email.code")` that reads `outboxId` and calls `OutboxJobProcessor.process`, letting exceptions propagate so the message is rejected to `tickgeek.email.code.dead`; add `CodeEmailListenerTest` per R18, contracts/email-messaging.md: Consumer rules (missing)
- [X] T091 Create `BE/module/email/OutboxSweeper.java`: `@Scheduled(fixedDelayString = "${tickgeek.email.sweep-interval}")`, calling a new native `OutboxRepository.claimStuck(now, cutoff, limit)` (`UPDATE outboxes SET next_attempt_at = :now WHERE id IN (SELECT … status = 'PENDING' AND next_attempt_at < :cutoff ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED) RETURNING id`) in one transaction, then publishing `OutboxJobScheduled(id, Duration.ZERO)` per id; add `OutboxSweeperTest` per R20 (missing)
- [X] T092 Delete `BE/module/email/OutboxWorker.java` and `BT/module/email/OutboxWorkerTest.java`; update the `SchedulingConfig` Javadoc ("outbox sweeper, cleanup") per R18 "OutboxWorker removed" (contradicts)
- [X] T093 Update the test support: add a `@ServiceConnection` RabbitMQ Testcontainer (a shared `TestRabbit.CONTAINER` like `TestPostgres`) to `BT/support/AbstractIntegrationTest.java`; replace the injected `OutboxWorker` and its manual runs with a helper that waits (Awaitility, a few seconds) for `RecordingEmailSender` to receive the email; purge the email queues in `@BeforeEach`; use a short `tickgeek.email.retry-base-delay` (e.g. 200ms) in tests per Constitution V, R14 (missing)
- [X] T094 Port `BT/integration/OutboxWorkerIT.java` to `BT/integration/OutboxConsumerIT.java`, keeping its five cases (replaced code cancelled with payload erased; a send after replacement doesn't activate; exhausted retries invalidate the code; …) but driven by messages, not the poller, per R14 Outbox (partial)
- [X] T095 Create `BT/integration/EmailMessagingIT.java` with the R14 messaging cases: a committed request is sent with scheduling disabled; a rolled-back request publishes nothing; a failed send returns through the delay queue and succeeds; the same message delivered twice sends one email; a job whose message was lost (row inserted with no publish, `next_attempt_at` older than stuck-after) is re-published by `OutboxSweeper` and sent; an unreadable message lands in `tickgeek.email.code.dead`. Per R14, R18–R20 (missing)
- [X] T096 Run `./mvnw verify` in `tickgeek_backend/` and confirm all tests pass and new-code line coverage ≥ 80% per Constitution V (missing)
- [X] T097 Add RabbitMQ to the local setup in the root `README.md` (the `docker run … rabbitmq:4-management` command from quickstart.md and the `TICKGEEK_RABBITMQ_*` variables) per quickstart: Prerequisites (missing)

---

## Phase 8: Narrow code-request locks

A per-source lock and a per-email lock replace the single global lock, and the system-wide count
runs unlocked (plan revised 2026-11-16; research R5, R14). Write each test task before the code
it covers: T098–T100 first (in parallel), then T101, then T102. T103 can be done at any time and
T104 last. T098 fails and T099 does not compile until T101 and T102 are done.

- [X] T098 [P] Update `BT/module/auth/code/CodeRequestServiceTest.java`: `issuesAPendingHashedCodeAndQueuesTheEmail` expects `lock.acquire("ana@example.com", SOURCE)` (the normalized email, then the source) before the limit check, in place of `lock.acquire()` per R5 Concurrency (contradicts)
- [X] T099 [P] Add `aHeldLockBlocksOnlyRequestsForTheSameEmail` to `BT/integration/CodeRequestConcurrencyIT.java`: on a background thread, inside a `TransactionTemplate` built from the injected `PlatformTransactionManager`, call `CodeRequestLockRepository.acquire("ana@x.com", "198.51.100.1")` and keep the transaction open until a latch is released; start a request for `ana@x.com` from `198.51.100.2` on a second thread and wait (Awaitility) until `pg_stat_activity` shows one session of the current database with `wait_event_type = 'Lock' AND wait_event = 'advisory'`; assert a request for `bob@x.com` from `198.51.100.3` returns 202 while the first request is still not done; release the latch in a `finally` and assert the waiting request then returns 202. Change the class Javadoc to say "per-source and per-email advisory locks" per R14 Concurrency, R5 (missing)
- [X] T100 [P] Relax `systemWideCapHoldsAcrossSources` in `BT/integration/GlobalLimitConcurrencyIT.java` and rename it `systemWideCapIsExceededByAtMostTheParallelThreads`: the number of 202 responses is at least the cap (10) and at most the cap plus `CodeRequestConcurrencyIT.THREADS`, and `count(verification_codes)` equals that number, in place of exactly 10 per R14 Concurrency, R5 "The system-wide cap is approximate" (contradicts)
- [X] T101 Rework `BE/module/auth/code/CodeRequestLockRepository.java`: replace `acquire()` and `CODE_REQUEST_LOCK_KEY` with `acquire(String email, String source)`, still `@Transactional(propagation = Propagation.MANDATORY)`. It runs `SELECT pg_advisory_xact_lock(?, ?)` twice: first with `(SOURCE_LOCKS, source.hashCode())`, then with `(EMAIL_LOCKS, email.hashCode())`. `SOURCE_LOCKS` and `EMAIL_LOCKS` are two distinct named `int` constants. The Javadoc states the fixed order (source, then email), that the order prevents deadlocks, and that a hash collision only makes requests wait, per R5 Concurrency (contradicts)
- [X] T102 Update `request(...)` in `BE/module/auth/code/CodeRequestService.java`: normalize the email first, call `lock.acquire(normalizedEmail, source)`, and only then read `clock.instant()` and build the `CodeRequestContext`. The limit checks, `invalidateLive`, and the inserts stay as they are, per R5 "Only after both locks does the transaction read `now`" (contradicts)
- [X] T103 [P] Update the Javadoc of `BE/module/auth/limit/implement/hourlylimit/extend/GlobalHourlyLimit.java`: the system-wide count runs without a lock, so a burst can exceed the cap by about the number of code requests in flight. No behaviour change, per R5 "The system-wide cap is approximate" (missing)
- [X] T104 Run `./mvnw verify` in `tickgeek_backend/` and confirm all tests pass, including the unchanged exact checks in `BT/integration/CodeRequestConcurrencyIT.java` (`parallelRequestsForOneEmailLeaveOneLiveCode`, `oneSourceGetsExactlyTwentyAccepted`), and that new-code line coverage is ≥ 80% per Constitution V (missing)

---

## Phase 9: Convergence

RabbitMQ removed; code emails are sent in-process by an after-commit `@Async` dispatcher (plan
revised 2026-11-16; research R21, R14 "Email dispatch"). Write each test task before the code it
covers and confirm it fails first: T105–T106 before T108–T110. T113 runs last and also closes
T096 and T104.

- [X] T105 Write `BT/module/email/OutboxJobDispatcherTest.java` (replacing `OutboxMessagePublisherTest` and `CodeEmailListenerTest`, which are deleted): an `OutboxJobScheduled` with delay zero calls `OutboxJobProcessor.process(id)` directly; a delay above zero schedules `process(id)` on the retry `TaskScheduler` at `clock.instant() + delay` and does not call it straight away. Also unit-test the executor's rejection handler: a rejected task logs and throws nothing per R21 (missing)
- [X] T106 Rewrite `BT/integration/EmailMessagingIT.java` with the R14 "Email dispatch" cases and no broker: a committed request is sent with scheduling disabled; a rolled-back request dispatches nothing; a failed send is retried by the retry scheduler (advance the `MutableClock` past the backoff) and succeeds; the same job dispatched twice sends one email; a job inserted with no event and `next_attempt_at` older than stuck-after is sent by `OutboxSweeper`; a job rejected by a full email executor (e.g. executor threads and queue capacity set to 1 with a blocked sender) stays `PENDING` and is sent by the sweeper. Remove `anUnreadableMessageEndsUpInTheDeadLetterQueue` and the delay-queue wording; keep `theSweeperLeavesJobsThatAreNotOverdue` per R14, R21 Testing (partial)
- [X] T107 Update `BT/support/AbstractIntegrationTest.java`: remove the `RabbitMQContainer`, `AmqpAdmin`, `RabbitListenerEndpointRegistry`, queue purges and listener start/stop, and the `spring.rabbitmq.*` test property; delete `BT/support/TestRabbit.java`; update the `RETRY_BASE_DELAY` comment (no delay queues). Update `BT/integration/OutboxConsumerIT.java` Javadoc and comments from "consumer/message" to "dispatch" without changing its five cases; add the new `executor`/`retry-scheduler-threads` values to `BT/support/TestProperties.email()` per R21 Testing ("No Testcontainer besides PostgreSQL") (contradicts)
- [X] T108 Add the R21 configuration: in `BE/config/EmailProperties.java` add a nested `Executor` record (`threads`, `queueCapacity`, `shutdownTimeout`) and `retrySchedulerThreads`; in `BR/application.yaml` add `tickgeek.email.executor.threads: 4`, `queue-capacity: 100`, `shutdown-timeout: 10s` and `tickgeek.email.retry-scheduler-threads: 1` per data-model: Configuration values (missing)
- [X] T109 Create `BE/config/EmailAsyncConfig.java`: `@EnableAsync`; an `EMAIL_EXECUTOR` constant naming a dedicated `ThreadPoolTaskExecutor` bean (core = max = `executor.threads`, queue `executor.queue-capacity`, a rejection handler that logs a warning and discards the task, `setWaitForTasksToCompleteOnShutdown(true)` with `executor.shutdown-timeout`); and a dedicated `ThreadPoolTaskScheduler` bean for email retries with `retry-scheduler-threads` threads, created regardless of `tickgeek.scheduling.enabled`. Neither replaces Boot's `applicationTaskExecutor` or the `@Scheduled` scheduler per R21 "Email executor", "Retry scheduler" (missing)
- [X] T110 Create `BE/module/email/OutboxJobDispatcher.java`: one `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async(EmailAsyncConfig.EMAIL_EXECUTOR)` method taking `OutboxJobScheduled`; delay zero → `OutboxJobProcessor.process(outboxId)`; delay above zero → schedule `process(outboxId)` on the email retry scheduler at `clock.instant() + delay`. Delete `BE/module/email/OutboxMessagePublisher.java`, `CodeEmailListener.java`, `OutboxJobMessage.java` and `BE/config/EmailMessagingConfig.java` per R21 Decision step 2 (contradicts)
- [X] T111 Remove RabbitMQ from the build and config: delete `spring-boot-starter-amqp` and test-scoped `testcontainers-rabbitmq` from `tickgeek_backend/pom.xml` (keep Awaitility); delete the `spring.rabbitmq` block from `BR/application.yaml`. Update the Javadoc of `BE/module/email/CodeEmailQueue.java`, `OutboxJobScheduled.java`, `OutboxSweeper.java` ("in-memory task was lost", not "message") and `OutboxJobProcessor.java` (retry via the retry scheduler, "dispatches" not "consumers/messages") to cite R21 per plan: Primary Dependencies, Storage; R20 update (contradicts)
- [ ] T112 Remove RabbitMQ from the docs and local setup: the RabbitMQ service, its `depends_on` and the `TICKGEEK_RABBITMQ_*` variables in `compose.yaml`; the RabbitMQ mentions, `docker run … rabbitmq` command and variables in the root `README.md` (added by T097); the `TICKGEEK_RABBITMQ_*` row in `tickgeek_backend/docs/deployment.md`. Also record `compose.yaml`, `tickgeek_backend/Dockerfile`, `tickgeek_frontend/Dockerfile` and their `.dockerignore` files in plan.md "Project Structure" (they are not listed there) per data-model "Removed", plan: Project Structure (contradicts, unrequested)
- [X] T113 Run `./mvnw verify` in `tickgeek_backend/`: all tests pass, only the PostgreSQL Testcontainer starts, no class imports `org.springframework.amqp`, and line coverage is ≥ 80%; then mark T096 and T104 done per Constitution V, R21 Testing (missing)
