# Test comments

### Test comments say what the test proves and why that matters
- Doesn't work: a comment that only lists the cases, so the reader sees numbers but not why
  they matter:
  ```java
  /**
   * The resend cooldown is 60 seconds: 59 s after the last request is blocked with 1 s left,
   * 60 s is allowed, and a first request is always allowed.
   */
  ```
- Works: the first sentence says what the test proves and the problem it protects against.
  The cases come after it:
  ```java
  /**
   * Proves the resend cooldown lets one email get a new code at most once per 60 seconds, so
   * nobody can flood someone's inbox or run up email costs, and a real user can retry after a
   * minute.
   * <p>
   * Cases: 59 s → blocked, 1 s left, wait shown (the only limit allowed to show it);
   * 60 s → allowed; first request → allowed.
   */
  ```
- Why: when the user highlights a comment, it should tell them why the test exists without
  them having to read the code.
- Applies to: test comments and Javadoc only. Main code, config and SQL get no comments (see
  "Only test code gets comments").

### Purpose before mechanics
- Doesn't work: answering "why did you write this test" with how it works: the mocked
  repository, the 59 s / 60 s assertions, the `disclosesWait` flag.
- Works: answer in this order, as in "it proves one email can't get a new code more than once
  a minute, so nobody can flood an inbox":
  1. What it is, in one line.
  2. Why it exists: the real-world problem it prevents.
  3. How it works, only if asked or after the first two.
- Why: the user had to ask three times before getting the purpose. The details mean nothing
  until the reason is clear.
- Applies to: every explanation of code or a decision, not only tests.

### Explain a new concept with a failure story from the user's own code
- Doesn't work: explaining integration testing with an abstract line ("checks the real parts
  work together where they meet"), a comparison table and a tour of the setup code. The user
  replied "I just don't get it … why not unit test only?"
- Works: start with one concrete bug from their project that a unit test would miss. For
  example, `CodeRequestServiceTest` mocks the lock, so it passes even if Postgres never
  actually blocks the second of two parallel requests, and the user gets two live codes. Then
  give the general rule, and only after that the mechanics.
- Why: a new concept only makes sense once the user sees the problem it solves happen in code
  they know. Abstract definitions and tables don't show that problem.
- Applies to: every explanation of a concept, tool or practice that is new to the user.

### Keep explanations of test plumbing short
- Doesn't work: a full explanation of `@DynamicPropertySource` (problem, second problem, how it
  works in four bullets, why "dynamic", alternatives). The user asked for it again in caveman
  mode.
- Works: a few short lines: what breaks without it, the code, two or three facts that matter.
  Give more only if asked.
- Why: the long version hid the main point. The user had to ask for a simpler version.
- Applies to: every explanation, not only tests.

### Use a child-level analogy only when the user asks for one
- Doesn't work: after two "explain like I am 5" requests, explaining `ApplicationContextIT`
  with a toy shelf and a robot without being asked. The user said "Hey, I'm not 5 again,
  explain more technical."
- Works: by default, explain technically (what Spring, Flyway and Hibernate do, in order, and
  what fails). Use an everyday picture only when the user asks for it ("like I'm 5", "simple"),
  and only for that one answer.
- Why: "explain like I'm 5" was a request for one answer, not a standing preference. Using it
  without being asked talks down to the user and leaves out the detail they want.
- Applies to: every explanation.

### Spec references in code comments name the full file path
- Doesn't work: a bare reference that doesn't say which spec it means, such as
  `(research R17)`, `(FR-008, contracts/auth-api.md 429)` or `(User Story 3)`. With several
  spec folders, an agent can't tell which document to open.
- Works: the comment says the rule in plain words, then the full path of the file plus the ID:
  `Only the code request endpoint is public; everything else is denied
  (specs/001-request-code/research.md R17).` Check that the ID still exists in that file.
- Why: the user said agents must be able to navigate to the referenced document; a reference
  without its spec folder is useless to them. This replaced an earlier "remove all references"
  request (2026-11-19).
- Applies to: test comments and Javadoc.

### Only test code gets comments
- Doesn't work: comment blocks in main code and config, e.g. the four-line comment above
  `hikari.data-source-properties` in `application.yaml` explaining `lock_timeout`, and the
  Javadoc on `CodeRequestLockRepository.lock()` explaining SQLSTATE 55P03. The user deleted the
  yaml comment and said the comment rules are for tests, not code or config. The cause was the
  "Applies to: all comments" lines in this file, which stretched the test rules to everything.
- Works: write no comments in main Java code, `application*.yaml` or SQL unless the user asks
  for one. Put the reasoning in the Javadoc of the test that proves the behaviour, or explain
  it in chat when asked.
- Why: the user wants comments only where tests explain what they prove; elsewhere they are
  noise.
- Applies to: `/impl`, `/speckit-implement` and every code or config edit.

### Say what problem a change solves before explaining the change
- Doesn't work: explaining the outbox UUID change with headings like "Problem 1: `save()` ran
  an extra query because the id was already set" and going straight into `isNew`, `merge`,
  B-tree pages and UUID bit layouts. The user replied "I don't know what problem we are
  resolving right now."
- Works: open with the problem in plain words and how serious it is, before any mechanism:
  "Nothing was broken. Every code request did one useless database query, and the outbox
  table's index would get slower as it grows. The change removes both." Then, only if the user
  wants it, how each part works, one at a time.
- Why: the mechanics mean nothing until the user knows what goes wrong without the change and
  whether it matters. This is the second correction of "Purpose before mechanics" in this file.
- Applies to: every explanation of a change, a fix or a review finding.
