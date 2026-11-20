# Quickstart: Validate Requesting a Sign-In Code

How to prove the code request works end to end. Endpoint details are in
[contracts/auth-api.md](contracts/auth-api.md); tables are in [data-model.md](data-model.md).

## Prerequisites

- Java 25, Node.js 26 LTS, Docker (for Testcontainers), PostgreSQL running locally with the
  `tickgeek` database from the root [README](../../README.md).
- The backend needs a code secret of at least 32 bytes:

  ```bash
  export TICKGEEK_AUTH_CODE_SECRET="dev-only-change-me-32-bytes-minimum"
  ```

  The `local` Spring profile sets a dev-only default instead, and relaxes the resend cooldown to
  1 s and the per-source cap to 200 per hour. Run without it to check the real limits by hand.

## 1. Automated checks

```bash
cd tickgeek_backend
./mvnw verify                 # unit + integration tests, fails if line coverage < 80%

cd ../tickgeek_frontend
npm ci && npm run lint && npm run build
```

Expected: everything passes. The integration suite includes the risky-boundary tests listed in
[research.md](research.md) R14: concurrency, spoofed forwarding headers, security matchers and
the schema check. Only a PostgreSQL container is started.

## 2. Run the app

```bash
cd tickgeek_backend && ./mvnw spring-boot:run
cd tickgeek_frontend && npm run dev
```

Open <http://localhost:3000/sign-in>.

## 3. Manual scenarios

| # | Steps | Expected |
|---|---|---|
| 1 | Enter ` Ana@Example.com ` and continue | "We sent a 6-digit code to ana@example.com. It is valid for 10 minutes."; `verification_codes` has one `PENDING` row for `ana@example.com`; `outboxes` has one `PENDING` row linked to it, whose payload holds the code |
| 2 | Submit an empty email, then `not-an-email` | An error under the field each time; no new rows |
| 3 | Continue again with the same email at once | "Please wait … seconds before requesting another code." |
| 4 | After 60 s, continue again with the same email | Accepted; the earlier code row is `INVALIDATED`, the new one is `PENDING` |
| 5 | Request 6 codes for one email within an hour (60 s apart) | The 6th says "Too many requests. Please try again later." |
| 6 | Start the backend with `TICKGEEK_TRUSTED_PROXIES='10\.255\.255\.1'` (so localhost is not trusted), then `curl -X POST localhost:8080/api/auth/code-requests -H 'Content-Type: application/json' -H 'X-Forwarded-For: 1.2.3.4' -d '{"email":"bob@x.com"}'` | `verification_codes.request_source` is `127.0.0.1`, not `1.2.3.4` |
| 7 | `curl -i localhost:8080/api/auth/anything` | 403 |
