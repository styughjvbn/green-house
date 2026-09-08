-- Match the repository's operation/key lookup. Never discard historical effects
-- to make a stronger uniqueness constraint pass.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM work_applied_effects
        GROUP BY work_operation_id, effect_key HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate Work effect identities require review before migration';
    END IF;
END $$;

ALTER TABLE work_applied_effects ADD COLUMN command_fingerprint VARCHAR(64);
ALTER TABLE work_applied_effects ADD CONSTRAINT ck_work_effect_fingerprint
    CHECK (command_fingerprint IS NULL OR command_fingerprint ~ '^[0-9a-f]{64}$');
ALTER TABLE work_applied_effects ALTER COLUMN effect_key TYPE VARCHAR(110);
ALTER TABLE work_applied_effects ADD CONSTRAINT uk_work_applied_effect_operation_key
    UNIQUE (work_operation_id, effect_key);
ALTER TABLE work_applied_effects DROP CONSTRAINT uk_work_applied_effect_operation_key_kind;
