-- Imported documents follow the same action policies as completed records.
-- Preserve document/item/lot identities, amounts, timestamps and all inventory/receipt history.
ALTER TABLE sales_slips DROP CONSTRAINT ck_sales_slips_historical_auction_import;
ALTER TABLE sales_slips DROP COLUMN historical_auction_import;
