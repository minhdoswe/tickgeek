-- Release 2 of 2: every code must point to its outbox, and the old outbox → code link goes
-- (specs/001-request-code/research.md R9, R15).
-- Deploy only after release 1 (V3) is fully live: the backend running during this deploy must
-- already write verification_codes.outbox_id and no longer use outboxes.verification_code_id.
-- Flyway runs this file in one transaction, so no BEGIN/COMMIT here.

-- Copy links for codes the pre-release-1 backend created while release 1 was deploying.
UPDATE verification_codes vc
SET outbox_id = o.id
FROM outboxes o
WHERE o.verification_code_id = vc.id
  AND vc.outbox_id IS NULL;

-- Every code must have an outbox. Fails, and the whole migration rolls back, if any code still
-- has none.
ALTER TABLE verification_codes ALTER COLUMN outbox_id SET NOT NULL;

-- Remove the old link; its foreign key and the V2 index are dropped with it.
ALTER TABLE outboxes DROP COLUMN verification_code_id;
