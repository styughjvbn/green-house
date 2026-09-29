ALTER TABLE work_operations
    DROP CONSTRAINT ck_work_operations_relation_type;

UPDATE work_operations
SET relation_type = 'MOVEMENT_DISCARD',
    details = CASE
        WHEN details IS NULL THEN NULL
        ELSE jsonb_set(details, '{relation}', '"MOVEMENT_DISCARD"'::jsonb, TRUE)
    END
WHERE relation_type = 'MOVEMENT_PRE_DISCARD';

ALTER TABLE work_operations
    ADD CONSTRAINT ck_work_operations_relation_type
        CHECK (relation_type IS NULL OR relation_type IN ('MOVEMENT_DISCARD'));
