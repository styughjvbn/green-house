-- Keep explicit legacy display labels while paid amounts come from valid allocations.
-- A label never creates a payment fact or determines payment eligibility.
ALTER TABLE direct_sales ADD COLUMN unpaid_payment_label VARCHAR(255) NOT NULL DEFAULT '미입금';
UPDATE direct_sales sale SET unpaid_payment_label = document.payment_status
FROM sales_slips document WHERE document.id = sale.sales_slip_id;
