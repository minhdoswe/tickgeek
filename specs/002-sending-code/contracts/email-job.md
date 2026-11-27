# Email Job Contract (internal)

This feature adds no HTTP endpoint. These are the in-process interfaces between the code request
(auth module) and the email job (email module). Reasons are in [research.md](../research.md).

## `OutboxJobScheduled` (event, email module)

```java
record OutboxJobScheduled(UUID outboxId, Duration delay)
```

- Published inside a transaction. The job runs `delay` after that transaction commits. If the
  transaction rolls back, nothing runs.
- Holds no email address or code.

## `EmailSender` (email module, `module/email/emailsender`)

```java
void send(String payload, String to);   // renamed from sentEmail
```

| Outcome | Meaning to the job |
|---|---|
| returns normally | a provider accepted the email |
| throws any exception | the attempt failed; it is retried while attempts are left (R6) |

Rules for every implementation:
- Make one provider call per `send`. The call's time limit stays inside the implementation
  (FR-003).
- Log the name of the provider that accepted (R7).
- Never put the code or the payload in an exception message or a log line. The only exception is
  `LoggingEmailSender`, which runs in the `local` profile only (FR-012, R9).

Implementations: `LoggingEmailSender` (`@Profile("local")`). Brevo comes later.

- `payload` is the outbox payload as stored (`{"to": …, "code": …}`). `to` is read from it by
  `CodeEmailComposer.recipientOf(payload)` in `EmailJobProcessor`.
- Each implementation builds the email text from the payload: the 6-digit code, that it is
  valid for 10 minutes, in English (FR-011).


## `OutboxJobOwner` (port: interface in the email module, implemented in the auth module)

```java
boolean isStillWanted(UUID outboxId);
void delivered(UUID outboxId, Instant acceptedAt);
void abandoned(UUID outboxId);
```

| Method | Called | `CodeOutboxJobOwner` does |
|---|---|---|
| `isStillWanted` | TX 1, before each attempt | true if the code linked to this outbox is `PENDING`, or if no code is linked |
| `delivered` | TX 2, after the provider accepted | `activatePending(id, acceptedAt, acceptedAt + code-ttl)` on that code (conditional UPDATE, R3) |
| `abandoned` | TX 2 or TX 1, when the job gives up | `invalidatePending(id)` on that code (conditional UPDATE, R3) |

- Each method runs inside the caller's transaction (propagation `MANDATORY`), so the outbox
  change and the code change commit together.
- The code is found with `VerificationCodeRepository.findByOutboxId`, which uses the unique index
  on `outbox_id`. A code is only ever changed by id, never by email.
