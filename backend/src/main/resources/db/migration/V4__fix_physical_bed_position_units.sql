-- Correct physical bed position unit counts and labels.
-- Maps beds by house number and bed number instead of assuming house IDs.

WITH corrected_beds (
    house_number,
    bed_number,
    position_unit_count
) AS (
    VALUES
        (1, 1, 24.00),
        (1, 2, 24.00),
        (1, 3, 24.00),

        (2, 1, 24.00),
        (2, 2, 24.00),
        (2, 3, 24.00),

        (3, 1, 24.00),
        (3, 2, 24.00),
        (3, 3, 24.00),

        (4, 1, 24.00),
        (4, 2, 24.00),
        (4, 3, 24.00),

        (5, 1, 24.00),
        (5, 2, 26.00),
        (5, 3, 28.00),

        (6, 1, 28.00),
        (6, 2, 28.00),
        (6, 3, 28.00),

        (7, 1, 28.00),
        (7, 2, 28.00),
        (7, 3, 28.00),

        (8, 1, 28.00),
        (8, 2, 28.00),
        (8, 3, 26.00),

        (9, 1, 24.00),
        (9, 2, 24.00),
        (9, 3, 24.00),

        (10, 1, 28.00),
        (10, 2, 28.00),
        (10, 3, 28.00),

        (11, 1, 28.00),
        (11, 2, 28.00),
        (11, 3, 28.00),

        (12, 1, 21.00),
        (12, 2, 21.00),
        (12, 3, 21.00),

        (13, 1, 21.00),
        (13, 2, 21.00),
        (13, 3, 21.00),

        (14, 1, 21.00),
        (14, 2, 21.00),
        (14, 3, 21.00),

        (15, 1, 21.00),
        (15, 2, 21.00),
        (15, 3, 21.00)
)
UPDATE physical_beds pb
SET
    position_unit_count = corrected_beds.position_unit_count,
    position_unit_label = '칸',
    updated_at = CURRENT_TIMESTAMP
FROM houses h,
     corrected_beds
WHERE pb.house_id = h.id
  AND h.number = corrected_beds.house_number
  AND pb.number = corrected_beds.bed_number;