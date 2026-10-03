-- Consolidated from V42__work_correction_audit_events.sql: preserve this stage's SQL order.
-- No historical correction conversion: stop rather than discard existing audit data.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM work_operation_corrections)
       OR EXISTS (SELECT 1 FROM work_operations o JOIN work_types t ON t.id = o.work_type_id WHERE t.code = 'CORRECTION')
       OR EXISTS (SELECT 1 FROM work_operations WHERE status = 'CORRECTED') THEN
        RAISE EXCEPTION 'Existing work corrections require a separate migration before V33';
    END IF;
END $$;
ALTER TABLE work_operation_corrections DROP COLUMN correction_work_operation_id;
ALTER TABLE work_operation_corrections
    ADD COLUMN worker VARCHAR(100),
    ADD COLUMN memo VARCHAR(1000),
    ADD COLUMN result_details JSONB NOT NULL,
    ADD COLUMN mutation_id BIGINT REFERENCES orchid_group_mutations(id),
    ADD COLUMN correlation_id UUID,
    ADD CONSTRAINT ck_work_correction_mutation_link CHECK ((mutation_id IS NULL) = (correlation_id IS NULL));
CREATE TABLE work_correction_receipts (
    request_key VARCHAR(100) PRIMARY KEY,
    request_fingerprint VARCHAR(64) NOT NULL,
    correction_id BIGINT REFERENCES work_operation_corrections(id),
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_work_correction_mutation ON work_operation_corrections (mutation_id) WHERE mutation_id IS NOT NULL;
CREATE UNIQUE INDEX uq_work_correction_receipt_event ON work_correction_receipts (correction_id) WHERE correction_id IS NOT NULL;
DELETE FROM work_types WHERE code = 'CORRECTION';
ALTER TABLE work_operations DROP CONSTRAINT ck_work_operations_status;
ALTER TABLE work_operations ADD CONSTRAINT ck_work_operations_status
    CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'PAUSED', 'COMPLETED', 'STOPPED', 'CANCELED', 'VOIDED'));
