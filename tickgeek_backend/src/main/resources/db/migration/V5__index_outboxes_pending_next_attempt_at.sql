-- The sweeper finds due PENDING jobs by next_attempt_at (specs/002-sending-code/research.md R1).
-- Partial, so finished jobs, which are most rows, stay out of the index.
CREATE INDEX outboxes_pending_next_attempt_at_idx
    ON outboxes (next_attempt_at)
    WHERE status = 'PENDING';
