-- Keep historical amounts unchanged. Audit and repair existing violations separately
-- before validating these constraints; NOT VALID still protects new/updated rows.
ALTER TABLE sales_slip_items
    ADD CONSTRAINT ck_sales_slip_items_amount CHECK (
        quantity > 0
        AND unit_price >= 0
        AND amount >= 0
        AND amount::bigint = quantity::bigint * unit_price::bigint
    ) NOT VALID;

ALTER TABLE sales_slips
    ADD CONSTRAINT ck_sales_slips_total_amount CHECK (total_amount >= 0) NOT VALID;
