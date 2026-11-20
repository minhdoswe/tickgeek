CREATE INDEX outboxes_verification_code_id_idx
    ON outboxes (verification_code_id)
    WHERE verification_code_id IS NOT NULL;
