ALTER TABLE work_operation_targets
    ADD COLUMN target_reference_type VARCHAR(30) NOT NULL DEFAULT 'ORCHID_GROUP',
    ADD COLUMN inbound_record_id BIGINT;

ALTER TABLE work_operation_targets
    ALTER COLUMN orchid_group_id DROP NOT NULL;

ALTER TABLE work_operation_targets
    ADD CONSTRAINT fk_work_operation_target_inbound_record
        FOREIGN KEY (inbound_record_id) REFERENCES inbound_records(id),
    ADD CONSTRAINT ck_work_operation_target_reference
        CHECK (
            (target_reference_type = 'ORCHID_GROUP' AND orchid_group_id IS NOT NULL AND inbound_record_id IS NULL)
            OR
            (target_reference_type = 'INBOUND_RECORD' AND orchid_group_id IS NULL AND inbound_record_id IS NOT NULL)
        );

CREATE INDEX idx_work_operation_targets_inbound_record
    ON work_operation_targets(inbound_record_id);
