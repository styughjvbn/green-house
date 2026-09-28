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
