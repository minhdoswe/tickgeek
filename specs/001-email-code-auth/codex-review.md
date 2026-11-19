# Codex Adversarial Review

Target: working tree diff
Verdict: needs-attention

Do not ship yet: restart recovery can outlast sign-in state, and the new auth refresh flow has no tests.

Findings:
- [high] Drain overdue email jobs across multiple batches (tickgeek_backend/src/main/java/minhdoswe/tickgeek/module/email/OutboxSweeper.java:38-39)
  Recovery dispatches only one batch of 20 jobs every two minutes. After a restart loses dispatches for 200 pending jobs, the final batch waits roughly 18 minutes even with instant delivery. The frontend sign-in cookie expires after 15 minutes, so those users receive codes they cannot submit through the existing flow. This also contradicts research.md's claimed four-minute recovery bound.
  Recommendation: Continue draining overdue batches with executor backpressure or schedule immediate follow-up sweeps. Add a restart recovery test with more than 200 pending jobs and verify delivery before sign-in state expires.
- [high] Test the new authenticated request retry branches (tickgeek_frontend/lib/api/authed.ts:18-31)
  This helper introduces request replay, token-cookie replacement, and session-clearing redirects without any tests. It also has no callers, so current application flows do not exercise it. The backend suite cannot verify these frontend branches; lint and build do not establish their behavior. AGENTS.md explicitly requires flagging new untested code paths as P1.
  Recommendation: Add executable tests for successful refresh with exactly one replay, missing or rejected refresh tokens, cookie updates and clearing, and propagation of dependency failures without replay.