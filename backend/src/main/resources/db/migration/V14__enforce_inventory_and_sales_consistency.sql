ALTER TABLE orchid_groups
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE sales_slips
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

-- NOT VALID preserves legacy rows while enforcing the invariant for new and changed rows.
ALTER TABLE orchid_groups
    ADD CONSTRAINT ck_orchid_groups_quantity_nonnegative
        CHECK (quantity >= 0) NOT VALID;

ALTER TABLE orchid_groups
    ADD CONSTRAINT ck_orchid_groups_reserved_quantity
        CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity) NOT VALID;

ALTER TABLE sales_slips
    ADD CONSTRAINT ck_sales_slips_sales_status
        CHECK (
            sales_status = '취소'
            OR sales_type IS NULL
            OR (sales_type = 'DIRECT' AND sales_status IN ('작성중', '출고 완료'))
            OR (sales_type = 'AUCTION' AND sales_status IN ('작성중', '출하 완료'))
        ) NOT VALID;
