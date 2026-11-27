# Quickstart: Validate Sending the Code Email

How to prove the code email job works end to end. Interfaces are in
[contracts/email-job.md](contracts/email-job.md); state changes are in
[data-model.md](data-model.md).

## Prerequisites

The same as [001 quickstart](../001-request-code/quickstart.md): Java 25, Docker for
Testcontainers, and a local PostgreSQL with the `tickgeek` database.

## 1. Automated checks

```bash
cd tickgeek_backend
./mvnw verify      # unit + integration tests, fails if line coverage < 80%
```

Expected: everything passes, including the job scenarios in [research.md](research.md) R10. The
integration tests use a fake `EmailSender` that can be told to accept or fail, so no provider is
contacted.

## 2. Run locally

```bash
cd tickgeek_backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Request a code from the sign-in page or with `POST /api/auth/code-requests`.

## 3. Manual scenarios (local profile)

| # | Steps | Expected |
|---|---|---|
| 1 | Request a code for `ana@example.com` | The app log shows the code email from `LoggingEmailSender` within a second. `outboxes`: `SENT`, `attempts = 1`, `published_at` set, `payload` NULL. `verification_codes`: `ACTIVE`, `sent_at` = `published_at`, `expires_at` = `sent_at + 10 min` |
| 2 | Look at the row from scenario 1 again | No further attempt ran; `attempts` is still 1 |
| 3 | Request again after the cooldown | The old code is `INVALIDATED`, the new one `ACTIVE`, each outbox `SENT` |

Failure, retry, give-up and replaced-code cases need a failing sender, so they are covered by
the integration tests only.

## 4. Known gaps to check by hand (not yet met)

| # | Steps | Expected today |
|---|---|---|
| G1 | Start without the `local` profile and request a code | No `EmailSender` exists. Probably fails when the job runs, not at startup. Accepted while the app runs only locally (plan.md OQ2) |
| G2 | Stop the app while a job waits for a retry, then start it | The retry is lost and the outbox stays `PENDING` (FR-009 gap, research R1) |
