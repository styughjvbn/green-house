-- Invalid legacy values must fail the migration instead of being discarded or inferred.
ALTER TABLE sales_slip_items
    ALTER COLUMN unit TYPE integer USING btrim(unit)::integer;

ALTER TABLE sales_slip_items
    ADD CONSTRAINT ck_sales_slip_item_unit_positive CHECK (unit IS NULL OR unit >= 1);
