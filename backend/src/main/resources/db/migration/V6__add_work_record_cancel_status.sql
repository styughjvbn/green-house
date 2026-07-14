ALTER TABLE work_records
  ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN canceled_at timestamp,
  ADD COLUMN cancel_reason text;

ALTER TABLE work_records
  ALTER COLUMN status DROP DEFAULT;
