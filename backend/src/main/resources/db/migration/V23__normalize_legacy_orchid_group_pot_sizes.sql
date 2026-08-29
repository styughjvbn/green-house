-- Normalize unambiguous legacy inch marks without changing the operational update timestamp.
UPDATE orchid_groups
SET pot_size = '3"',
    pot_size_code = 'POT_3'
WHERE pot_size_code = 'UNMAPPED'
  AND btrim(pot_size) IN ('3“', '3”');

UPDATE orchid_groups
SET pot_size = '4"',
    pot_size_code = 'POT_4'
WHERE pot_size_code = 'UNMAPPED'
  AND btrim(pot_size) IN ('4“', '4”');
