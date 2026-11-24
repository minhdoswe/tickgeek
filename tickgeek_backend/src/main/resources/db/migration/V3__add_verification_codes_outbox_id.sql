-- Release 1 of 2: a code now points to its outbox (verification_codes.outbox_id), because a code
-- always needs an email job but an outbox doesn't always belong to a code
-- (specs/001-request-code/research.md R9, R15).
-- Additive only, so the backend running before this deploy keeps working while the new one
-- starts: outbox_id stays nullable and outboxes.verification_code_id is kept. Release 2 copies
-- again, sets outbox_id NOT NULL and drops outboxes.verification_code_id.
-- Flyway runs this file in one transaction, so no BEGIN/COMMIT here.

-- No ON DELETE action: an outbox can't be deleted while a code points to it.
ALTER TABLE verification_codes
    ADD COLUMN outbox_id UUID REFERENCES outboxes (id);

-- Copy the existing links from the old column.
UPDATE verification_codes vc
SET outbox_id = o.id
FROM outboxes o
WHERE o.verification_code_id = vc.id;

-- One outbox per code; also serves the foreign key check when outboxes are deleted.
CREATE UNIQUE INDEX verification_codes_outbox_id_key
    ON verification_codes (outbox_id);
