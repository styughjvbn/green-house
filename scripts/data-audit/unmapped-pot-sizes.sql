SELECT
    g.id AS orchid_group_id,
    g.variety_name,
    g.pot_size,
    g.quantity,
    h.number AS house_number,
    b.number AS physical_bed_number,
    z.name AS bed_zone_name
FROM orchid_groups g
JOIN bed_zones z ON z.id = g.bed_zone_id
JOIN physical_beds b ON b.id = z.physical_bed_id
JOIN houses h ON h.id = b.house_id
WHERE g.pot_size_code = 'UNMAPPED'
ORDER BY h.number, b.display_order, z.sort_order, g.sort_order;
