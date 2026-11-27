# TickGeek Constitution

## Core Principles

### I. Clean Code

- Names MUST reveal intent: classes, methods, variables, and files are named for what they do or
  hold, without unexplained abbreviations.
- A function or method body MUST be ≤ 30 lines.
- Magic numbers and strings MUST NOT appear in logic; use named constants, enums, or
  externalized configuration.
- Dead code and commented-out code MUST NOT be merged.

**Rationale**: Small, well-named units are easier to read, review, test, and change safely.

### II. SOLID

- Controllers handle HTTP only. Business logic MUST live in services. Data access MUST happen
  only in repositories.
- Dependencies MUST be supplied by constructor injection; field `@Autowired` injection is
  prohibited.
- Behaviour MUST be extended by adding new classes (e.g. strategy or polymorphism), not by
  growing `if/else` chains or `switch` statements on types.
- Interfaces MUST be small and focused on a single client need.

**Rationale**: Clear responsibilities and boundaries keep each change local and make business
logic testable without HTTP or a database.

### III. DRY

- Each piece of knowledge (business rule, validation, constant) MUST have a single source of
  truth.

**Rationale**: When each rule lives in one place, changing it cannot leave stale copies behind.

### IV. Frontend Standards

- The TypeScript `any` type MUST NOT be used.
- Components are Server Components by default; `"use client"` MUST be added only when the
  component needs client-side state, effects, browser APIs, or event handlers.
- Each file MUST contain one component and be ≤ 200 lines.
- API calls MUST live only in `lib/api/`.

**Rationale**: Server-first rendering minimizes client JavaScript, and a single API layer keeps
the frontend consistent with the backend contract.

### V. Testing Discipline (NON-NEGOTIABLE)

- Every feature MUST include unit tests for its services.
- Every endpoint MUST have at least one integration test.
- Line coverage on new code MUST be ≥ 80%.

**Rationale**: Tests are the evidence that a feature works and keeps working as the code changes.

## Technology Stack

- Backend: Java 25 (LTS), Spring Boot 4.1.x, Maven 3.10.
- Frontend: Next.js 16.4.x, Node.js 26 LTS, TypeScript.
- Database: PostgreSQL.
- Messaging: RabbitMQ 4.x, accessed through Spring AMQP, for asynchronous background work.
- Introducing another language, framework, or datastore, or moving outside these version lines,
  requires a constitution amendment.

## Development Workflow & Quality Gates

- Work follows the Spec Kit flow: specify → (clarify) → plan → tasks → implement. Each plan MUST
  pass a Constitution Check against Principles I–VI.
- A change MUST NOT merge unless its tests pass, the 80% new-code coverage gate is met, and it
  complies with Principles I–VI.
- Any deviation from a principle MUST be justified in the plan's Complexity Tracking table and
  noted in the pull request.

## Governance

- This constitution supersedes other project practices and guidance.
- Amendments are made via `/speckit-constitution` in a pull request stating the change and its
  rationale.
- Versioning follows semantic versioning: MAJOR for removing or redefining a principle, MINOR for
  adding a principle or section or materially expanding guidance, PATCH for clarifications and
  wording fixes.
- Every pull request and every plan's Constitution Check MUST verify compliance; reviewers MUST
  block violations that lack a recorded justification.

**Version**: 2.2.0 | **Ratified**: 2026-11-11 | **Last Amended**: 2026-11-26
