-- Consolidated from V32__normalize_inbound_receipt_model.sql: preserve this stage's SQL order.
UPDATE inbound_records
SET status = 'POTTING_PENDING',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'TEMP_STORED';

UPDATE inbound_records
SET status = 'PLACED'
WHERE status = 'POTTED';

UPDATE orchid_groups AS orchid
SET inbound_record_id = inbound.id
FROM inbound_records AS inbound
WHERE inbound.created_orchid_group_id = orchid.id
  AND orchid.inbound_record_id IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM inbound_records AS inbound
        JOIN orchid_groups AS orchid ON orchid.id = inbound.created_orchid_group_id
        WHERE orchid.inbound_record_id IS DISTINCT FROM inbound.id
    ) THEN
        RAISE EXCEPTION '대표 입고 난 묶음의 입고 출처가 일치하지 않습니다.';
    END IF;
END $$;

-- Flush deferred ledger/FK checks before dropping a FK that refers to the
-- backfilled OrchidGroup rows. PostgreSQL rejects the DDL with pending events.
SET CONSTRAINTS ALL IMMEDIATE;

ALTER TABLE inbound_records
    DROP CONSTRAINT IF EXISTS inbound_records_bed_zone_id_fkey,
    DROP CONSTRAINT IF EXISTS inbound_records_created_orchid_group_id_fkey,
    DROP COLUMN bottle_count,
    DROP COLUMN actual_quantity,
    DROP COLUMN potting_date,
    DROP COLUMN pot_size,
    DROP COLUMN age_year,
    DROP COLUMN growth_stage,
    DROP COLUMN placement_type,
    DROP COLUMN tray_count,
    DROP COLUMN bed_zone_id,
    DROP COLUMN created_orchid_group_id;

-- Consolidated from V33__normalize_work_command_receipt_memberships.sql: preserve this stage's SQL order.
CREATE TABLE work_command_receipt_memberships (
    receipt_key VARCHAR(255) NOT NULL,
    operation_id BIGINT NOT NULL,
    PRIMARY KEY (receipt_key, operation_id),
    CONSTRAINT fk_work_command_receipt_membership_receipt
        FOREIGN KEY (receipt_key) REFERENCES work_command_receipts(receipt_key) ON DELETE CASCADE,
    CONSTRAINT fk_work_command_receipt_membership_operation
        FOREIGN KEY (operation_id) REFERENCES work_operations(id)
);

CREATE UNIQUE INDEX idx_work_command_receipt_memberships_operation
    ON work_command_receipt_memberships(operation_id);

INSERT INTO work_command_receipt_memberships (receipt_key, operation_id)
SELECT receipt.receipt_key, result.operation_id::BIGINT
FROM work_command_receipts receipt
CROSS JOIN LATERAL jsonb_array_elements_text(receipt.result_operation_ids) result(operation_id)
WHERE result.operation_id ~ '^[0-9]+$'
  AND EXISTS (SELECT 1 FROM work_operations operation WHERE operation.id = result.operation_id::BIGINT)
ON CONFLICT DO NOTHING;
