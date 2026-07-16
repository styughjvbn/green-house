ALTER TABLE work_target_executions
    ADD COLUMN processed_quantity INTEGER NOT NULL DEFAULT 0;

ALTER TABLE work_target_executions
    ADD CONSTRAINT ck_work_target_executions_processed_quantity
        CHECK (processed_quantity >= 0);
