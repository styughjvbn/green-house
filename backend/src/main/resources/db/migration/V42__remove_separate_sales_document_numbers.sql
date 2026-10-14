-- Documents are identified by their existing primary keys. Keep historical receipt/audit JSON intact.
ALTER TABLE sales_slips DROP COLUMN slip_number;
DROP TABLE sales_slip_daily_sequences;
