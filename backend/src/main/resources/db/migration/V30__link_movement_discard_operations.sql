ALTER TABLE work_operations
    ADD COLUMN parent_operation_id BIGINT,
    ADD COLUMN relation_type VARCHAR(40);

ALTER TABLE work_operations
    ADD CONSTRAINT fk_work_operations_parent
        FOREIGN KEY (parent_operation_id) REFERENCES work_operations(id),
    ADD CONSTRAINT ck_work_operations_relation_pair
        CHECK ((parent_operation_id IS NULL) = (relation_type IS NULL)),
	ADD CONSTRAINT ck_work_operations_relation_type
		CHECK (relation_type IS NULL OR relation_type IN ('MOVEMENT_DISCARD'));

CREATE INDEX idx_work_operations_parent_relation
    ON work_operations(parent_operation_id, relation_type, id);

UPDATE work_operations
SET parent_operation_id = (details ->> 'movementOperationId')::BIGINT,
	relation_type = 'MOVEMENT_DISCARD'
WHERE details ->> 'relation' IN ('MOVEMENT_DISCARD', 'MOVEMENT_PRE_DISCARD')
  AND details ->> 'movementOperationId' ~ '^[0-9]+$'
  AND EXISTS (
      SELECT 1
      FROM work_operations parent
	  WHERE parent.id = (work_operations.details ->> 'movementOperationId')::BIGINT
  );
