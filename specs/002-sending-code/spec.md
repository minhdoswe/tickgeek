# Feature Specification: Email Code Sign-In: Send the Code Email

**Feature Branch**: `feat/send_confirmation_email`

**Created**: 2026-11-25

**Status**: Draft

**Input**: User description: "Feature 002-sending-code. Scope: only the job that receives the
in-process event published when a user submits their email (OutboxJobScheduled from feature 001,
carrying the outbox id) and delivers the confirmation email containing the 6-digit verification
code to the user. Delivery uses email providers in priority order: Brevo (1), then MailerSend
(2). Each attempt tries Brevo first; if it fails, MailerSend. If both fail, retry later with
delay 5 × 2^n (n from 0, retry 4 times). Each retry repeats the same order (Brevo first, then
MailerSend). When all retries fail, invalidate the code. Out of scope: anything the user sees on
screen, entering/verifying the code, bounce tracking."

This spec picks up where [001-request-code](../001-request-code/spec.md) stops: a code and its
queued email job exist, and the system is told the job is ready. This feature delivers that email
and records the outcome. It adds no screens and no endpoints.

## Clarifications

### Session 2026-11-25

- Q: What unit is the retry delay (5 × 2ⁿ) in? → A: Seconds: retries after 5, 10, 20 and 40 s.
- Q: When the user requests a new code while the old code's email is still waiting for a retry,
  what happens to the old job? → A: It is cancelled; no email with the old code is sent.
- Q: When a provider rejects the recipient address permanently, is that retried? → A: No. The
  system stops at once, invalidates the code and records the job as failed.

### Session 2026-11-26

- Q: Which email providers deliver code emails? → A: Brevo only. MailerSend is dropped, so there
  is no fallback inside an attempt: a failed Brevo call goes straight to the retry schedule. The
  quoted input above is kept as written. FR-005 and SC-002 were deleted.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The code email is delivered (Priority: P1)

Right after a user asks for a sign-in code, the email carrying that code is handed to the
email provider, Brevo. As soon as Brevo accepts the email, the code becomes usable and its
10-minute lifetime starts.

**Why this priority**: Without delivery the code request from 001 is useless: nobody can sign in.

**Independent Test**: Queue a code email for `ana@example.com` with a stand-in for Brevo; confirm
the email went out once and the code is live with an expiry 10 minutes after Brevo accepted it.

**Acceptance Scenarios**:

1. **Given** a queued code email and Brevo accepting emails, **When** the job runs, **Then** Brevo
   receives one email to the recipient containing the 6-digit code, and the code becomes
   live, expiring 10 minutes after Brevo accepted it.
2. **Given** a queued code email and Brevo refusing it or not answering in time, **When** the job
   runs, **Then** the attempt counts as failed and a retry is scheduled as in User Story 2.
3. **Given** Brevo has accepted the email, **When** the job finishes, **Then** the job is
   recorded as sent, along with which provider sent it, and the code and recipient address are
   no longer stored on the job.

---

### User Story 2 - Retry with growing waits when Brevo fails (Priority: P1)

If Brevo fails during an attempt, the system waits and tries again, waiting longer each time.

**Why this priority**: Provider outages and rate limits are usually short. Retrying lets most
codes still arrive without the user having to ask again.

**Independent Test**: Make Brevo fail for the first two attempts and accept on the third;
confirm the attempts happened after the expected waits and the code became live after the
third.

**Acceptance Scenarios**:

1. **Given** Brevo fails on the first attempt, **When** the attempt ends, **Then** the
   next attempt is scheduled 5 seconds later, and later retries wait 10, 20 and 40 seconds
   (5 × 2ⁿ seconds, n = 0–3).
2. **Given** a retry succeeds, **When** Brevo accepts, **Then** the outcome is the same as in
   User Story 1, and no further retries run.

---

### User Story 3 - Give up and invalidate the code (Priority: P1)

When the first attempt and all 4 retries have failed, or Brevo rejects the recipient address
outright, the system stops trying, invalidates the code and records the job as failed.

**Why this priority**: A code that can never arrive must not stay live; it would block nothing
useful and hold a stored secret for no purpose.

**Independent Test**: Make Brevo fail on every attempt; confirm exactly 5 attempts
(5 provider calls) were made, then the code is invalidated, the job is recorded as failed with
the last error, and no code or recipient address remains on the job.

**Acceptance Scenarios**:

1. **Given** Brevo has failed on the first attempt and on each of the 4 retries,
   **When** the last retry ends, **Then** no further attempt is scheduled, the code is
   invalidated, and the job is recorded as failed with the reason for the last failure.
2. **Given** the job has failed for good, **When** its stored data is inspected, **Then** neither
   the code nor the recipient address is stored on it, and the recorded error contains neither.
3. **Given** Brevo rejects the recipient address as one that cannot receive email, **When** that
   answer arrives, **Then** no retry is scheduled, the code is
   invalidated and the job is recorded as failed with the rejection as its reason.

---

### User Story 4 - Stop sending a code that was replaced (Priority: P2)

If the user asks for a new code while the old code's email is still waiting for a retry, the old
email job is cancelled, so the user never receives a code that no longer works.

**Why this priority**: A dead code arriving next to the live one confuses the user, but it does
not stop them signing in with the new code.

**Independent Test**: Make Brevo fail on the first attempt for code A, then request code
B for the same email; confirm code A's job is recorded as cancelled, makes no further provider
calls and no longer holds its code or recipient, while code B's email is sent.

**Acceptance Scenarios**:

1. **Given** code A's email is waiting for a retry, **When** code B is requested for the same
   email, **Then** code A's job is recorded as cancelled and no further attempt for it contacts
   any provider.
2. **Given** code A's job has been cancelled, **When** its stored data is inspected, **Then**
   neither the code nor the recipient address is stored on it.

---

### Edge Cases

- **Code replaced during an attempt that is already talking to a provider**: that one email may
  still go out with the old code; no attempt starts after the code was replaced.
- **Permanent rejection versus provider trouble**: only a rejection of the recipient address
  itself ends the job at once (User Story 3, scenario 3). Problems on the provider's side (wrong
  credentials, quota or rate limits, outages, errors, no answer) count as an ordinary failure, followed
  by a retry.
- **Brevo does not answer**: after a set time limit the call counts as a failure and a retry is
  scheduled.
- **The system restarts or is redeployed while a job is waiting for a retry**: the job is not
  lost. It runs at its scheduled time, or as soon as possible after the system is back if that
  time has passed, and its attempt count is not reset.
- **More than one copy of the system is running**: each attempt for a job runs only once.
- **A provider accepts the email but the system stops before recording it**: the job may be sent
  again on the next attempt. The user then gets two emails with the same code, which is accepted.
- **A job has already finished (sent, failed or cancelled) and is announced again**: nothing is
  sent.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST start delivering a code email as soon as the code request that
  created it has been saved, without the user waiting for delivery.
- **FR-002**: The system MUST deliver code emails through one email provider, Brevo.
- **FR-003**: Each attempt MUST make one call to Brevo. A call that is refused, returns an error or
  does not answer within a configurable time limit MUST count as a failed attempt.
- **FR-004**: If an attempt fails, the system MUST schedule another attempt
  after a wait of 5 × 2ⁿ seconds (n = 0 for the first retry: 5, 10, 20, 40 s), up to 4 retries
  (5 attempts in total). The base wait and the number of retries MUST be configurable.
- **FR-006**: When Brevo accepts the email, the system MUST make the code live with an expiry
  10 minutes (the configured code lifetime) after the moment of acceptance, record the job as sent
  with the time and the provider, and stop all further attempts.
- **FR-007**: After the last allowed attempt fails, the system MUST invalidate the code and record
  the job as failed with the reason for the last failure.
- **FR-007a**: When Brevo rejects the recipient address as unable to receive email, the system
  MUST stop at once without scheduling a retry, invalidate the code and record the job as failed with the rejection as its reason. Every other
  provider failure MUST be handled as in FR-003 and FR-004.
- **FR-007b**: When a code stops being pending before its email has been accepted (because a newer
  code was requested), the system MUST cancel its email job and MUST NOT start any further
  attempt for it.
- **FR-008**: When a job finishes, whatever the outcome, the system MUST remove the code and the
  recipient address from it. Recorded errors and logs MUST NOT contain the code.
- **FR-009**: Scheduled attempts MUST survive a restart of the system, and each attempt MUST run
  only once even when more than one copy of the system is running.
- **FR-010**: A job that has already finished MUST NOT be sent again when it is announced again.
- **FR-011**: The email MUST contain the 6-digit code, state that it is valid for 10 minutes, and
  be in English (001 FR-024).
- **FR-012**: In the local development setup, code emails MUST be written to the application log
  instead of being sent through Brevo.

### Key Entities

- **Email job** (exists in 001): one queued email carrying one code. This feature uses and
  updates its status (pending, sent, failed, cancelled), number of attempts, time of the next
  attempt, last error, time sent, and adds which provider sent it.
- **Verification code** (exists in 001): this feature moves it from pending to live (setting its
  sent time and expiry) or to invalidated.
- **Email provider**: the outside service that accepts an email for delivery (Brevo). Key
  attribute: name.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: While Brevo is working, 95% of code emails are accepted by Brevo within 5 seconds
  of the code request.
- **SC-003**: No code is ever live unless a provider accepted its email.
- **SC-004**: When Brevo keeps failing, each code email gets exactly 5 attempts and
  5 provider calls within about 75 seconds of the request, and its code is invalidated after the
  last one.
- **SC-006**: A rejected recipient address costs exactly one provider call.
- **SC-007**: No attempt starts for a code email after that code was replaced by a newer one.
- **SC-005**: No finished job holds a code or recipient address.

## Assumptions

- "Fails" means the provider did not accept the email for delivery: it refused it, returned an
  error, or did not answer in time. Whether the email later reaches the inbox or bounces is not
  tracked (out of scope).
- Code emails are sent from one sender name and address. The sending domain is set up with
  Brevo, and Brevo's credentials are supplied to the system as a secret.
- Out of scope: anything the user sees on screen (including any notice that sending failed),
  entering or checking the code, and bounce or complaint tracking.
- A code request whose email failed still counts toward the per-email limits of 001 (every
  accepted request counts).
- Depends on 001: the code request saves the code and its email job together, then announces the
  job by its id. The announcement carries no email address or code.
