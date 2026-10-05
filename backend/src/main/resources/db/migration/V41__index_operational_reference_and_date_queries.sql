-- Transactional index build: deploy during a write maintenance window.
-- Fail promptly on lock contention; an interrupted migration rolls back all indexes.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE INDEX idx_orchid_groups_zone_sort ON orchid_groups (bed_zone_id, sort_order);
CREATE INDEX idx_orchid_groups_active_zone ON orchid_groups (bed_zone_id) WHERE quantity > 0;
CREATE INDEX idx_orchid_groups_inbound ON orchid_groups (inbound_record_id, id);
CREATE INDEX idx_sales_items_slip ON sales_slip_items (sales_slip_id, id);
CREATE INDEX idx_sales_allocations_item ON sales_slip_item_allocations (sales_slip_item_id, id);
CREATE INDEX idx_sales_allocations_group ON sales_slip_item_allocations (orchid_group_id);
CREATE INDEX idx_sales_movements_slip_type ON sales_inventory_movements (sales_slip_id, change_type);
CREATE INDEX idx_sales_movements_group ON sales_inventory_movements (orchid_group_id);
CREATE INDEX idx_settlement_lines_parent ON auction_settlement_lines (settlement_id, id);
CREATE INDEX idx_settlement_lines_lot ON auction_settlement_lines (auction_shipment_lot_id);
CREATE INDEX idx_sales_slips_date ON sales_slips (sale_date DESC, id DESC);
CREATE INDEX idx_sales_slips_partner_date ON sales_slips (partner_id, sale_date DESC, id DESC);
CREATE INDEX idx_settlements_date ON auction_settlements (auction_date DESC, id DESC);
CREATE INDEX idx_inbound_records_date ON inbound_records (inbound_date DESC, id DESC);
CREATE INDEX idx_payment_events_date ON partner_payment_events (event_date DESC, id DESC);
CREATE INDEX idx_auction_shipments_date_id ON auction_shipments (shipment_date DESC, id DESC);
CREATE INDEX idx_lineage_mutation_result ON orchid_group_lineage (mutation_id, result_orchid_group_id, id);
