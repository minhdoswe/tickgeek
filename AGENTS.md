## Code Review Rules

Code in this repo is written by an AI agent. Review it as an
independent second opinion; do not assume the PR description is accurate.

### Correctness
- Flag behaviour the PR claims but the diff does not implement (P0).
- Flag new code paths without tests (P1).

### Agent failure modes
- Flag stubs, placeholders, or TODOs presented as finished work (P0).
- Flag tests deleted, skipped, or weakened to make them pass (P0).
- Flag swallowed errors and silent fallbacks (P1).
- Flag changes outside the PR's stated scope (P1).
- Flag calls to APIs or packages that are not in the lockfile (P0).

### Security and data
- No secrets or PII in logs.
- Every new route must go through the auth middleware.

### Out of scope
- Do not comment on formatting or naming; CI handles those.