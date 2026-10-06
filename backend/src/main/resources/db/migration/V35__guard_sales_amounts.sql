-- Preserve historical signed returns without rewriting quantities, amounts or payments.
-- Enforce exact multiplication in BIGINT to reject both positive and negative INTEGER
-- overflow. The API retains its normal-sale rules; the future return model is separate.
ALTER TABLE sales_slip_items
    ADD CONSTRAINT ck_sales_slip_items_amount CHECK (
        quantity <> 0
        AND unit_price >= 0
        AND amount::bigint = quantity::bigint * unit_price::bigint
    ) NOT VALID;
