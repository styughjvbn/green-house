ALTER TABLE orchid_groups
    ADD COLUMN reserved_quantity INTEGER NOT NULL DEFAULT 0;

CREATE TABLE sales_slip_item_allocations (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    sales_slip_item_id BIGINT NOT NULL REFERENCES sales_slip_items(id) ON DELETE CASCADE,
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    allocated_quantity INTEGER NOT NULL
);

INSERT INTO sales_slip_item_allocations (created_at, updated_at, sales_slip_item_id, orchid_group_id, allocated_quantity)
SELECT now(), now(), id, orchid_group_id, quantity
FROM sales_slip_items
WHERE orchid_group_id IS NOT NULL;

UPDATE orchid_groups g
SET reserved_quantity = reserved.reserved_quantity
FROM (
    SELECT allocation.orchid_group_id, SUM(allocation.allocated_quantity) AS reserved_quantity
    FROM sales_slip_item_allocations allocation
    JOIN sales_slip_items item ON item.id = allocation.sales_slip_item_id
    JOIN sales_slips slip ON slip.id = item.sales_slip_id
    WHERE COALESCE(slip.sales_status, '') NOT IN ('출고 완료', '출하 완료')
    GROUP BY allocation.orchid_group_id
) reserved
WHERE g.id = reserved.orchid_group_id;

ALTER TABLE sales_slip_items
    DROP COLUMN orchid_group_id;

CREATE TABLE sales_inventory_movements (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    orchid_group_id BIGINT NOT NULL REFERENCES orchid_groups(id),
    sales_slip_id BIGINT NOT NULL REFERENCES sales_slips(id),
    sales_slip_item_id BIGINT NOT NULL REFERENCES sales_slip_items(id),
    change_type VARCHAR(50) NOT NULL,
    quantity_delta INTEGER NOT NULL,
    memo TEXT
);
