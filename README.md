# TickGeek

Event ticketing for organizers.

- `tickgeek_backend/`: Spring Boot 4.1.x API (Java 25, Maven 3.10, PostgreSQL, Flyway, RabbitMQ)
- `tickgeek_frontend/`: Next.js 16.4.x app (Node.js 26 LTS, TypeScript)
- `specs/`: feature specifications, plans and contracts

## Sign-in

Sign-in is passwordless. Users enter their email, receive a 6-digit code and enter it. New users
then give their first and last name. See [the auth API](tickgeek_backend/docs/api/auth.md).

## Run everything with Docker

Starts PostgreSQL, RabbitMQ, the backend and the frontend:

```bash
docker compose up --build        # add -d to run in the background
```

Open <http://localhost:3000/sign-in>. Sign-in codes are written to the backend log, not emailed:

```bash
docker compose logs -f backend
```

The backend API is on port 8080 and the RabbitMQ UI is at <http://localhost:15672>
(tickgeek / tickgeek_password). `docker compose down` stops everything; add `-v` to also delete
the database. This setup uses dev-only secrets and is not for production.

## Local development

Create the database once:

```bash
createuser -P tickgeek            # password: tickgeek_password
createdb -O tickgeek tickgeek
```

Start RabbitMQ, which carries code emails from the request to the email sender. The backend
creates its queues at startup. The management UI is at <http://localhost:15672> (guest / guest).

```bash
docker run -d --name tickgeek-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

The backend connects to `localhost:5672` as `guest` by default; elsewhere set
`TICKGEEK_RABBITMQ_HOST`, `TICKGEEK_RABBITMQ_PORT`, `TICKGEEK_RABBITMQ_USER` and
`TICKGEEK_RABBITMQ_PASSWORD`.

Run the backend with the `local` profile, which writes code emails (including the code) to the
log instead of sending them:

```bash
cd tickgeek_backend
export TICKGEEK_AUTH_CODE_SECRET="dev-only-change-me-32-bytes-minimum"
export TICKGEEK_JWT_SECRET="dev-only-jwt-secret-at-least-32-bytes!"
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Run the frontend:

```bash
cd tickgeek_frontend
cp .env.example .env.local
npm install
npm run dev
```

Open <http://localhost:3000/sign-in>.

## Checks

```bash
cd tickgeek_backend && ./mvnw verify      # unit + integration tests (Docker needed), fails below 80% line coverage
cd tickgeek_frontend && npm run lint && npm run build
```

Deployment settings and the required environment variables are in
[tickgeek_backend/docs/deployment.md](tickgeek_backend/docs/deployment.md).
