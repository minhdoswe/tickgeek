# Quickstart: Validate Email Code Sign-In and Registration

How to prove the feature works end to end. Endpoint details are in
[contracts/auth-api.md](contracts/auth-api.md); tables are in [data-model.md](data-model.md).

## Prerequisites

- Java 25, Node.js 26 LTS, Docker (for Testcontainers), PostgreSQL running locally with the
  `tickgeek` database from the root [README](../../README.md).
- Environment variables for the backend:

  ```bash
  export TICKGEEK_AUTH_CODE_SECRET="dev-only-change-me-32-bytes-minimum"
  export TICKGEEK_JWT_SECRET="dev-only-jwt-secret-at-least-32-bytes!"
  export BREVO_API_KEY="<sandbox key>"          # or run with the 'local' profile below
  ```

- With the `local` Spring profile, emails are written to the backend log instead of being sent,
  so no Brevo key is needed.

## 1. Automated checks

```bash
cd tickgeek_backend
./mvnw verify                 # unit + integration tests, fails if new-code coverage < 80%

cd ../tickgeek_frontend
npm ci && npm run lint && npm run build
```

Expected: everything passes. The integration suite includes the required risky-boundary tests
listed in [research.md](research.md) R14: concurrency (per-email, per-source, system-wide within
its bound, requests for different emails not waiting on each other, parallel guesses, double
verification, double registration), stale outbox jobs, email dispatch (sent after commit,
scheduled retry, duplicate dispatch, sweeper recovery, full executor), spoofed forwarding
headers, and security matchers. Only a PostgreSQL container is started.

## 2. Run the app

```bash
cd tickgeek_backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd tickgeek_frontend && npm run dev
```

Open <http://localhost:3000/sign-in>.

## 3. Manual scenarios

| # | Steps | Expected |
|---|---|---|
| 1 | Enter a new email → read the code from the backend log → enter it → enter first/last name | Lands on `/dashboard`, signed in; `users` has one verified row |
| 2 | Sign out, then sign in with ` NEW@Example.com ` (different case and spaces) | No name step; same account; still one `users` row |
| 3 | Open `/dashboard` while signed out | Redirected to `/sign-in?next=/dashboard`; after sign-in lands on `/dashboard` |
| 4 | Visit `/sign-in?next=//evil.example` and sign in | Lands on `/dashboard` |
| 5 | Request a code, then try "Resend" immediately | Resend disabled with a countdown; enabled after 60 s; the old code returns "expired" |
| 6 | Enter a wrong code 5 times | Attempts-left message each time; 5th says request a new code; the correct code no longer works |
| 7 | Request codes for an existing and a new email | Same screens and messages for both |
| 8 | New email: verify code, close the tab on the name step, reopen `/sign-in/name` within 10 min | Name step resumes; after 10 min it redirects to `/sign-in` |
| 9 | Sign in on two browsers, sign out in one | The other stays signed in; the signed-out browser's refresh token is revoked in `refresh_tokens` |
| 9b | Stay on `/dashboard` for over 15 minutes, then navigate | Still signed in (access token refreshed silently); `refresh_tokens.last_used_at` moved |
| 11 | Request a code and watch the backend log | The code email is logged within about a second, with no 5 s polling; `outboxes` shows `SENT` with a NULL payload. (A resend that cancels an unsent job is now too fast to see by hand; it is covered by the outbox integration tests in R14.) |
| 11b | Run without the `local` profile and with `BREVO_API_KEY=invalid`, request a code, then stop the backend within 30 s and start it again | After the restart, `outboxes.attempts` keeps rising (the lost in-memory retry is picked up by the sweeper within about 4 minutes, R20/R21); after the 5th failure, `outboxes` is `ERROR` and the code is `INVALIDATED` |
| 12 | Start the backend with `TICKGEEK_TRUSTED_PROXIES='10\.255\.255\.1'` (so localhost is not trusted), then `curl -X POST localhost:8080/api/auth/code-requests -H 'X-Forwarded-For: 1.2.3.4' ...` | `verification_codes.request_source` is `127.0.0.1`, not `1.2.3.4` (automated in `CodeRequestEndpointIT`) |
| 13 | `curl localhost:8080/api/auth/me` with no token, or with a JWT whose signature was edited | 401 `UNAUTHENTICATED` |

## 4. Session timeout check

Set `tickgeek.auth.session-idle-timeout=2m` and `tickgeek.auth.access-token-ttl=1m`, sign in,
wait 3 minutes, reload `/dashboard`: the refresh is rejected and you are redirected to sign-in. The integration test covering this uses a controllable clock instead of
waiting.
