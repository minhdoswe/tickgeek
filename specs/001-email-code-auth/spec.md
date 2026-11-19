# Feature Specification: Email Code Sign-In and Registration

**Feature Branch**: `001-email-code-auth`

**Created**: 2026-11-11

**Status**: Draft

**Input**: User description: "a login / register that let user to enter email, after they click next or register/login, an email is sent to their email and they enter 6 digit code to verify (i learn from evenbrite)"

## Clarifications

### Session 2026-11-11

- Q: Can one account be signed in on several devices at once, and what does signing out end? → A: Multiple sessions allowed; signing out ends only the current device's session.
- Q: After a user finishes signing in or registering, where should they land? → A: Back to the protected page that sent them to sign in; otherwise the organizer home page.
- Q: Which language or languages should the sign-in screens and code emails support? → A: English only for V1.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a new account with an email code (Priority: P1)

A first-time visitor enters their email address and continues. TickGeek emails them a 6-digit
code. They type the code into the page; when it matches, they are asked for their first and last
name. On submitting the name, their account is created, their email is marked as verified, and
they are signed in.

**Why this priority**: Without account creation no organizer can use TickGeek. It also proves
the user owns the email address, which later features (creating organizations) depend on.

**Independent Test**: Using an email with no account, enter the email, read the code from the
received message, enter it, enter a first and last name, and confirm a verified account with that
name exists and the user is signed in.

**Acceptance Scenarios**:

1. **Given** no account exists for `ana@example.com`, **When** the user enters that email and
   continues, **Then** a 6-digit code is emailed to that address and the user sees a code-entry
   screen that names the address the code was sent to.
2. **Given** a code was just sent, **When** the user enters the correct code before it expires,
   **Then** they are asked for their first and last name, and no account exists yet.
3. **Given** the user verified their code and is on the name step, **When** they submit a first
   and last name, **Then** an account is created with that name and a verified email, and the
   user is signed in.
4. **Given** the user is on the name step, **When** they submit an empty first or last name,
   **Then** they see which field is missing and no account is created.
5. **Given** two flows for the same new email have both verified a code and are on the name
   step, **When** both submit names at nearly the same time, **Then** exactly one account is
   created with the first-processed name, and both flows end signed in to that account.
6. **Given** a code was sent, **When** the user enters a wrong code, **Then** they see an error,
   no account is created, and they can try again (within the attempt limit).

---

### User Story 2 - Sign in to an existing account with an email code (Priority: P1)

A returning user enters the same email they registered with. TickGeek emails a new 6-digit code;
entering it signs them in to their existing account. The first screen is identical for new and
returning users — the user does not have to choose "register" or "log in".

**Why this priority**: Returning organizers must be able to get back in; equal to P1 because the
single combined flow is the core of the feature.

**Independent Test**: With an existing verified account, enter its email, enter the received
code, and confirm the user is signed in to that same account (no duplicate account created).

**Acceptance Scenarios**:

1. **Given** an account exists for `ana@example.com`, **When** the user enters `ANA@example.com `
   (different case, trailing space) and the correct code, **Then** they are signed in to the
   existing account immediately (no name step) and no second account is created.
2. **Given** a user signed in on a laptop and a phone, **When** they sign out on the laptop,
   **Then** the laptop's session ends and protected pages there require signing in again, while
   the phone stays signed in.
3. **Given** a signed-in user whose last signed-in request was more than 30 days ago, **When**
   they open a protected page, **Then** they are asked to sign in again.
4. **Given** a signed-in user who makes a signed-in request on day 29, **When** they return on
   day 45, **Then** they are still signed in (the 30 days restart from day 29).
5. **Given** a signed-out user opens a protected page (e.g. create event), **When** they finish
   signing in, **Then** they land on that page; **Given** they started from the sign-in screen
   directly, **Then** they land on the organizer home page.

---

### User Story 3 - Recover from a missing or expired code (Priority: P2)

A user who did not receive the code, or whose code expired, can request a new one from the
code-entry screen, or go back and correct a mistyped email address.

**Why this priority**: Email delivery is not instant or guaranteed; without resend and
change-email the user is stuck, but the core flow works without it.

**Independent Test**: Request a code, wait out the resend cooldown, request again, and confirm
only the newest code works.

**Acceptance Scenarios**:

1. **Given** a code was sent less than 60 seconds ago, **When** the user views the code-entry
   screen, **Then** the resend option is disabled and shows the remaining wait time.
2. **Given** the cooldown has passed, **When** the user requests a new code, **Then** a new code
   is emailed and any earlier code for that email stops working.
3. **Given** a code has expired, **When** the user enters it, **Then** they are told the code
   expired and offered a new one.
4. **Given** the user is on the code-entry screen, **When** they choose to change their email,
   **Then** they return to the email screen with the previous address prefilled.

---

### Edge Cases

- The email address is malformed or empty → the user is told before any code is sent.
- The user enters a non-numeric or wrong-length code → rejected without counting as an attempt.
- Five wrong codes in a row → the code is invalidated; the user must request a new one.
- Codes are requested too often for one email (more than 5 per hour) → further requests are
  refused with a message saying when they can try again.
- Two browser tabs request codes for the same email → only the most recently sent code is valid.
- A code is verified successfully → it is used up; entering it again fails, including from
  another tab.
- The email provider fails to deliver → the user sees the same "code sent" message (failures
  are often only known later); the request still counts toward the resend wait and hourly
  limit, the code stays valid in case the email arrives late, and the user can resend once the
  wait ends. No account is created until a code is verified.
- Codes are requested for many different emails from one source, or system-wide volume spikes →
  further requests are refused with a generic "try again later" message (FR-020).
- The sign-in link carries a return destination outside TickGeek → it is ignored and the user
  lands on the organizer home page.
- Someone enters another person's email → no account is created and nothing is revealed about
  whether that email is registered.
- A new user closes the page on the name step → no account is created; returning in the same
  browser before the registration permission expires resumes the name step, otherwise they start
  over.
- A new user submits the name step twice (double click, two tabs) → exactly one account is
  created and the user ends signed in to it.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST offer a single entry screen asking only for an email address, used
  for both registration and sign-in.
- **FR-002**: The system MUST validate the email format and normalize it (trim whitespace,
  compare case-insensitively) before use.
- **FR-003**: After the user continues with a valid email, the system MUST email a 6-digit
  numeric code to that address.
- **FR-004**: Until a code has been verified, the system MUST behave identically whether or not
  an account exists for the email: same screens, wording, outcome status, resend behaviour and
  limits, and no consistent difference in response time. After successful verification the flow
  MAY differ (name step for new users, immediate sign-in for existing users), since the user has
  proven they own the email.
- **FR-005**: A code MUST expire 10 minutes after it is sent. A correct code MUST be used up at
  the moment it is verified and cannot be verified again.
- **FR-006**: Requesting a new code MUST invalidate all earlier unused codes for that email.
- **FR-007**: The system MUST allow at most 5 incorrect entries per code, after which the code is
  invalidated.
- **FR-008**: The system MUST enforce a 60-second cooldown between code requests for the same
  email and at most 5 code requests per email per hour. Every accepted request counts toward
  these limits, even if the email later fails to deliver; a code whose email failed remains
  valid until it expires or is replaced.
- **FR-009**: When a correct, unexpired code is entered for an email with no account, the system
  MUST proceed to the name step (FR-018) and, once it is completed, create an account with that
  email marked as verified and sign the user in.
- **FR-010**: When a correct, unexpired code is entered for an email with an existing account,
  the system MUST sign the user in to that account without creating another one.
- **FR-011**: The system MUST NOT create an account until a code has been verified and, for new
  users, the name step has been completed.
- **FR-012**: Users MUST be able to resend a code and change the email address from the
  code-entry screen.
- **FR-013**: A user MAY be signed in on several devices or browsers at once, each with its own
  session; signing in on one MUST NOT end the others. Users MUST be able to sign out, which ends
  only the session on the current device or browser: that device is signed out immediately,
  and the session can no longer be renewed. A short-lived access credential already issued to
  that session MAY keep working for at most 15 minutes if it was copied elsewhere.
- **FR-014**: A signed-in session MUST end 30 days after its last signed-in request. Only
  requests made while signed in (pages or actions that require an account) count as activity and
  restart the 30 days; visiting public pages or background requests the user did not trigger do
  not. A session ended by timeout requires signing in again.
- **FR-015**: Error messages MUST state what went wrong and what to do next (wrong code, expired
  code, too many attempts, too many requests) without exposing whether an account exists.
- **FR-016**: The code email MUST contain the code, how long it is valid, and a note that the
  recipient can ignore it if they did not request it.
- **FR-017**: Sign-in MUST use email codes only; accounts MUST NOT have passwords, and no
  password entry, setup, or reset screens exist.
- **FR-018**: After a correct code is entered for an email with no account, the system MUST ask
  for the user's first and last name (both required, 1–50 characters each, surrounding
  whitespace trimmed) before creating the account. Existing users MUST NOT see this step.
- **FR-019**: When a code is verified for an email with no account, the system MUST issue a
  single-use registration permission, held only by the browser that verified the code, that
  allows completing the name step for that email. It expires at the verified code's original
  expiry time and is used up when the account is created. If the user leaves and returns in that
  browser before it expires, the name step resumes; otherwise they must request a new code.
- **FR-020**: In addition to the per-email limits, the system MUST limit code requests per
  source (e.g. network address or device) across all emails and MUST cap total code emails sent
  system-wide per time window. Exact thresholds MUST be configurable. Refusals MUST use a generic
  "try again later" message that does not reveal which limit was hit or whether an account
  exists.
- **FR-021**: If two flows holding valid registration permissions for the same email submit the
  name step concurrently, the system MUST create exactly one account (keeping the name from the
  first processed submission) and sign both flows in to that account.
- **FR-022**: After successful sign-in or registration, the system MUST return the user to the
  protected TickGeek page that sent them to sign in, if any; otherwise to the organizer home
  page. The return destination MUST be a TickGeek page; any other destination is ignored and the
  user goes to the organizer home page.
- **FR-023**: *(Removed 2026-11-17: security events are no longer recorded.)*
- **FR-024**: All sign-in screens, messages, and code emails MUST be in English for V1.

### Key Entities

- **User account**: A person who can sign in. Key attributes: normalized email (unique), first
  name, last name, verified status, created time. Has no password.
- **Verification code**: A one-time 6-digit code tied to one email address. Key attributes:
  email, code, sent time, expiry time, failed-attempt count, status (active, used, invalidated,
  expired).
- **Registration permission**: A single-use proof, created when a new user verifies a code,
  that lets that browser complete the name step. Key attributes: email, expiry time (the code's
  original expiry), status (active, used, expired).
- **Session**: Proof that a user is signed in on one device or browser; a user can have several
  at once. Key attributes: user, created time, last activity, ended status.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new user can go from entering their email to being signed in, including the
  name step, within 2 minutes, excluding email delivery time.
- **SC-002**: 95% of code emails arrive within 1 minute of being requested.
- **SC-003**: 90% of users who request a code complete sign-in or registration on their first
  attempt.
- **SC-004**: Zero duplicate accounts exist for the same normalized email, including under
  repeated or simultaneous submissions.
- **SC-005**: Before a code is verified, an observer cannot tell registered from unregistered
  emails by screens, wording, outcome status, resend behaviour, limits, or response time.
- **SC-006**: A code cannot be guessed: no more than 5 attempts per code and 5 codes per hour
  per email are ever accepted.

## Assumptions

- The flow follows the Eventbrite-style pattern: email first, then a one-time code; there is no
  separate "register" and "log in" form.
- Users are organizers; guests claiming tickets do not need accounts and are not part of this
  feature.
- An account created by this flow counts as email-verified, satisfying the rule that only
  verified users may create organizations.
- Code lifetime (10 minutes), attempt limit (5), resend cooldown (60 seconds), hourly cap (5),
  and session inactivity timeout (30 days) are defaults chosen from common practice and are
  configurable; per-source and system-wide limits (FR-020) are set during planning.
- Sessions use short-lived (15-minute) access credentials renewed by a longer-lived session
  credential (decided 2026-11-11), so a session's "last signed-in request" is measured to
  within 15 minutes, and sign-out may take up to 15 minutes to reach a copied access credential.
- The email provider may report delivery failures only after the request has completed, so the
  user cannot always be told immediately that an email failed.
- Passwords are intentionally not supported (decided 2026-11-11); this supersedes the
  email-and-password registration in the earlier product requirements.
- Social sign-in (Google, Apple), magic links, multi-factor authentication, editing the name
  after registration, and changing the email address of an existing account are out of scope.
- Emails are sent through the project's existing transactional email provider.
