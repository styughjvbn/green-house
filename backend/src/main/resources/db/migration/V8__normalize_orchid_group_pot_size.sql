ALTER TABLE orchid_groups
    ADD COLUMN pot_size_code VARCHAR(30);

UPDATE orchid_groups
SET pot_size_code = CASE
    WHEN pot_size IS NULL OR btrim(pot_size) = '' THEN 'UNSPECIFIED'
    WHEN lower(btrim(pot_size)) IN ('2', '2"', '2치', '2인치') THEN 'POT_2'
    WHEN lower(btrim(pot_size)) IN ('2.5', '2.5"', '2.5치', '2.5인치') THEN 'POT_2_5'
    WHEN lower(btrim(pot_size)) IN ('3', '3"', '3치', '3인치') THEN 'POT_3'
    WHEN lower(btrim(pot_size)) IN ('3.5', '3.5"', '3.5치', '3.5인치') THEN 'POT_3_5'
    WHEN lower(btrim(pot_size)) IN ('4', '4"', '4치', '4인치') THEN 'POT_4'
    WHEN lower(btrim(pot_size)) IN ('4.5', '4.5"', '4.5치', '4.5인치') THEN 'POT_4_5'
    WHEN lower(btrim(pot_size)) IN ('5', '5"', '5치', '5인치') THEN 'POT_5'
    WHEN lower(btrim(pot_size)) IN ('6', '6"', '6치', '6인치') THEN 'POT_6'
    WHEN lower(btrim(pot_size)) IN ('행잉', 'hanging') THEN 'HANGING'
    WHEN lower(btrim(pot_size)) IN ('기타', 'etc') THEN 'ETC'
    ELSE 'UNMAPPED'
END;

ALTER TABLE orchid_groups
    ALTER COLUMN pot_size_code SET NOT NULL;

ALTER TABLE orchid_groups
    ADD CONSTRAINT ck_orchid_groups_pot_size_code CHECK (pot_size_code IN (
        'UNSPECIFIED', 'UNMAPPED', 'POT_2', 'POT_2_5', 'POT_3',
        'POT_3_5', 'POT_4', 'POT_4_5', 'POT_5', 'POT_6', 'HANGING', 'ETC'
    ));

CREATE INDEX idx_orchid_groups_derived_group
    ON orchid_groups(variety_id, age_year, pot_size_code, status);
