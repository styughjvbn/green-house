-- Preserve the item name/genus snapshots. Attach identity only when evidence is unambiguous.
ALTER TABLE sales_slip_items ADD COLUMN variety_id bigint;

-- Only original creation snapshots establish a historical identity. Never reconstruct it from current groups.
WITH evidence AS (
    SELECT a.sales_slip_item_id, s.variety_id
    FROM sales_slip_item_allocations a
    LEFT JOIN sales_orchid_group_snapshots s
      ON s.sales_slip_item_allocation_id = a.id AND s.snapshot_type = 'CREATION'
     AND s.capture_source = 'LIVE'
), candidates AS (
    SELECT sales_slip_item_id, min(variety_id) AS variety_id
    FROM evidence
    GROUP BY sales_slip_item_id
    HAVING count(DISTINCT variety_id) = 1 AND count(*) = count(variety_id)
)
UPDATE sales_slip_items i SET variety_id = c.variety_id
FROM candidates c JOIN varieties v ON v.id = c.variety_id
WHERE i.id = c.sales_slip_item_id;

-- Records without inventory links can be matched by name + genus, ignoring internal whitespace.
-- Empty genus is allowed only when the name identifies one master row. Do not use aliases or fuzzy matches.
WITH candidates AS (
    SELECT i.id, min(v.id) AS variety_id
    FROM sales_slip_items i
    JOIN varieties v
      ON regexp_replace(lower(btrim(i.item_name)), '[[:space:] 　]+', '', 'g')
         = regexp_replace(lower(btrim(v.name)), '[[:space:] 　]+', '', 'g')
     AND (nullif(btrim(i.genus), '') IS NULL
          OR regexp_replace(lower(btrim(i.genus)), '[[:space:] 　]+', '', 'g')
             = regexp_replace(lower(btrim(v.genus)), '[[:space:] 　]+', '', 'g'))
    WHERE i.variety_id IS NULL
      AND NOT EXISTS (SELECT 1 FROM sales_slip_item_allocations a WHERE a.sales_slip_item_id = i.id)
    GROUP BY i.id HAVING count(*) = 1
)
UPDATE sales_slip_items i SET variety_id = c.variety_id
FROM candidates c WHERE i.id = c.id;

ALTER TABLE sales_slip_items ADD CONSTRAINT fk_sales_slip_items_variety
    FOREIGN KEY (variety_id) REFERENCES varieties(id);
CREATE INDEX idx_sales_slip_items_variety ON sales_slip_items(variety_id) WHERE variety_id IS NOT NULL;
