CREATE TABLE sales_slip_daily_sequences (
    sale_date date PRIMARY KEY,
    last_value bigint NOT NULL CHECK (last_value > 0)
);

INSERT INTO sales_slip_daily_sequences (sale_date, last_value)
SELECT
    sale_date,
    MAX(substring(slip_number from '-([0-9]+)$')::bigint)
FROM sales_slips
WHERE slip_number ~ '-[0-9]+$'
GROUP BY sale_date;
