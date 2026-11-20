# Feature Specification: Email Code Sign-In: Request a Code

**Feature Branch**: `001-email-code-auth`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "a login / register that let user to enter email, after they click next or register/login, an email is sent to their email and they enter 6 digit code to verify (i learn from evenbrite)"

This spec covers the part of sign-in that is built: the user enters an email and the system
creates a 6-digit code and queues the email that will carry it. Requirement IDs keep their
original numbers, so there are gaps.

## Clarifications

### Session 2026-11-11

- Q: Which language or languages should the sign-in screens and code emails support? → A: English only for V1.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Request a sign-in code (Priority: P1)

A visitor enters their email address and continues. TickGeek creates a 6-digit code for that
email, queues the code email, and tells the user that a code was sent and how long it is valid.
The screen is the same for everyone: the user does not choose "register" or "log in".

**Why this priority**: Every later sign-in step starts from a code request.

**Independent Test**: Enter an email on the sign-in screen and confirm the confirmation message
names the normalized address, and that one live code and one queued email job exist for it.

**Acceptance Scenarios**:

1. **Given** the sign-in screen, **When** the user enters ` Ana@Example.com ` and continues,
   **Then** they see "We sent a 6-digit code to ana@example.com. It is valid for 10 minutes.",
   and one live code and one queued email exist for `ana@example.com`.
2. **Given** the sign-in screen, **When** the user enters an empty or malformed email, **Then**
   they see what is wrong with it and no code is created.
3. **Given** a code was requested for an email, **When** a new code is requested for the same
   email after the cooldown, **Then** the earlier code stops being live and only the new one is.

---

### User Story 2 - Limits on code requests (Priority: P1)

Code requests are limited so nobody can flood an inbox or run up email costs. A user who asks
again too soon is told how long to wait; every other refusal says only "try again later".

**Why this priority**: Without limits the code request is an open spam and cost channel.

**Independent Test**: Request a code, request again at once and confirm the wait is shown; then
pass the per-email hourly cap and confirm a generic refusal.

**Acceptance Scenarios**:

1. **Given** a code was requested 15 seconds ago, **When** the same email asks again, **Then** the
   request is refused with "Please wait 45 seconds before requesting another code.".
2. **Given** 5 codes were requested for one email in the last hour, **When** a 6th is requested,
   **Then** it is refused with "Too many requests. Please try again later." and no wait time.
3. **Given** one source has made 20 requests in the last hour, **When** it requests a code for a
   new email, **Then** it is refused with the same generic message.

---

### Edge Cases

- The email address is malformed or empty → the user is told before any code is created.
- The same email is entered with different case or surrounding spaces → it is treated as one
  email.
- Two browser tabs request codes for the same email → only the most recent code is live.
- Codes are requested for many different emails from one source, or system-wide volume spikes →
  further requests are refused with a generic "try again later" message (FR-020).
- Someone enters another person's email → nothing is revealed about whether that email is
  registered.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST offer a single entry screen asking only for an email address, used
  for both registration and sign-in.
- **FR-002**: The system MUST validate the email format and normalize it (trim whitespace,
  compare case-insensitively) before use.
- **FR-003**: After the user continues with a valid email, the system MUST create a 6-digit
  numeric code for that address and queue an email carrying it.
- **FR-004**: The system MUST behave identically whether or not an account exists for the email:
  same screens, wording, outcome status and limits, and no consistent difference in response
  time.
- **FR-006**: Requesting a new code MUST invalidate all earlier unused codes for that email.
- **FR-008**: The system MUST enforce a 60-second cooldown between code requests for the same
  email and at most 5 code requests per email per hour. Every accepted request counts toward
  these limits.
- **FR-015**: Error messages MUST state what went wrong and what to do next, without exposing
  whether an account exists.
- **FR-020**: In addition to the per-email limits, the system MUST limit code requests per
  source (network address) across all emails and MUST cap total code requests system-wide per
  time window. Exact thresholds MUST be configurable. Refusals MUST use a generic
  "try again later" message that does not reveal which limit was hit or whether an account
  exists.
- **FR-024**: All sign-in screens and messages MUST be in English for V1.

### Key Entities

- **Verification code**: A one-time 6-digit code tied to one email address. Key attributes:
  email, code (stored only as a keyed hash), request source, request time, status.
- **Email job**: A queued email carrying one code to its address. Key attributes: the code it
  belongs to, the recipient and code (erased once the job finishes), status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-005**: An observer cannot tell registered from unregistered emails by screens, wording,
  outcome status, limits, or response time.
- **SC-006**: No more than 5 codes per hour per email are ever accepted.

## Assumptions

- The flow follows the Eventbrite-style pattern: email first, then a one-time code; there is no
  separate "register" and "log in" form.
- Users are organizers; guests claiming tickets do not need accounts.
- Resend cooldown (60 seconds), per-email cap (5 per hour), per-source cap (20 per hour) and
  system-wide cap (1,000 per hour) are configurable defaults.
- The per-source and system-wide caps may be exceeded slightly by a burst of parallel requests;
  the cooldown and per-email cap are exact.
- Sending the queued email, entering the code, accounts and sessions are not built yet.
