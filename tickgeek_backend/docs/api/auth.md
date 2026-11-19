# Auth API

TickGeek uses passwordless sign-in: the user enters an email, receives a 6-digit code and enters
it. New users then give their first and last name. The API is stateless: a 15-minute JWT access
token authenticates requests, and a per-device refresh token (30 days, sliding) renews it.

| Endpoint | Purpose |
|---|---|
| `POST /api/auth/code-requests` | Send a code to an email |
| `POST /api/auth/code-verifications` | Check a code; signs in or asks for a name |
| `POST /api/auth/registrations` | Finish the name step for a new account |
| `POST /api/auth/token-refreshes` | Get a new access token |
| `POST /api/auth/sign-out` | End this device's session |
| `GET /api/auth/me` | The signed-in user (needs `Authorization: Bearer <accessToken>`) |

The full contract (request and response bodies, error codes, cookies) is in
[specs/001-email-code-auth/contracts/auth-api.md](../../../specs/001-email-code-auth/contracts/auth-api.md).
