# Deployment

## Backend environment variables

| Variable | Required | Purpose |
|---|---|---|
| `TICKGEEK_AUTH_CODE_SECRET` | yes | HMAC key for stored sign-in codes. At least 32 bytes, random, never shared. |
| `TICKGEEK_JWT_SECRET` | yes | HS256 signing key for access tokens. At least 32 bytes. Changing it signs everyone out of their current access token (refresh tokens keep working). |
| `BREVO_API_KEY` | yes (except `local` profile) | Brevo transactional email API key. |
| `TICKGEEK_EMAIL_SENDER` | no | Sender address (default `no-reply@tickgeek.app`); must be a verified Brevo sender. |
| `TICKGEEK_TRUSTED_PROXIES` | no | Regex of addresses allowed to set `X-Forwarded-For` (default: localhost only). Set it to the Next.js server's address. |
| `TICKGEEK_DB_URL`, `TICKGEEK_DB_USER`, `TICKGEEK_DB_PASSWORD` | no | PostgreSQL connection (defaults match local development). |
| `TICKGEEK_RABBITMQ_HOST`, `TICKGEEK_RABBITMQ_PORT`, `TICKGEEK_RABBITMQ_USER`, `TICKGEEK_RABBITMQ_PASSWORD` | no | RabbitMQ connection for code emails (defaults `localhost`, `5672`, `guest`, `guest`). Use a dedicated user in production; `guest` only works from localhost. The backend declares its exchanges and queues at startup. |

The app refuses to start if either secret is shorter than 32 bytes.

## Frontend environment variables

| Variable | Purpose |
|---|---|
| `API_URL` | Backend base URL, e.g. `http://backend:8080/api`. Only called from the Next.js server. |
| `CLIENT_IP_HEADER` | Header that holds the real client IP, e.g. `x-real-ip`. |

## Client IP rule

Per-source rate limits depend on the client IP. The edge proxy in front of Next.js **must
overwrite** `CLIENT_IP_HEADER` with the connecting client's address, never append to it or pass
through a client-sent value. Next.js forwards exactly that one value as `X-Forwarded-For`, and the
backend only accepts it from `TICKGEEK_TRUSTED_PROXIES`. If the header is missing or not an IP,
the source is recorded as `unknown`.
