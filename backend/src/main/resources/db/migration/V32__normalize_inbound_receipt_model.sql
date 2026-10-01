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
