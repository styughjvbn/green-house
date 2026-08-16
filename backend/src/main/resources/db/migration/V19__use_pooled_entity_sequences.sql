ALTER TABLE audit_events ALTER COLUMN id DROP IDENTITY IF EXISTS;
CREATE SEQUENCE IF NOT EXISTS audit_events_id_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE audit_events_id_seq OWNED BY audit_events.id;
ALTER TABLE audit_events ALTER COLUMN id SET DEFAULT nextval('audit_events_id_seq');

DO $$
DECLARE
    sequence_row RECORD;
    table_max_id BIGINT;
    sequence_last_value BIGINT;
    sequence_is_called BOOLEAN;
    restart_value BIGINT;
BEGIN
    FOR sequence_row IN
        SELECT *
        FROM (VALUES
            ('auction_attempts', 'auction_attempts_id_seq'),
            ('auction_lot_status_history', 'auction_lot_status_history_id_seq'),
            ('auction_result_lines', 'auction_result_lines_id_seq'),
            ('auction_settlement_lines', 'auction_settlement_lines_id_seq'),
            ('auction_settlements', 'auction_settlements_id_seq'),
            ('auction_shipment_lots', 'auction_shipment_lots_id_seq'),
            ('auction_shipments', 'auction_shipments_id_seq'),
            ('audit_events', 'audit_events_id_seq'),
            ('bed_zone_capacities', 'bed_zone_capacities_id_seq'),
            ('bed_zones', 'bed_zones_id_seq'),
            ('business_partners', 'business_partners_id_seq'),
            ('houses', 'houses_id_seq'),
            ('inbound_records', 'inbound_records_id_seq'),
            ('materials', 'materials_id_seq'),
            ('orchid_group_collection_members', 'orchid_group_collection_members_id_seq'),
            ('orchid_group_collections', 'orchid_group_collections_id_seq'),
            ('orchid_group_lineage', 'orchid_group_lineage_id_seq'),
            ('orchid_groups', 'orchid_groups_id_seq'),
            ('partner_balance_summaries', 'partner_balance_summaries_id_seq'),
            ('partner_payment_events', 'partner_payment_events_id_seq'),
            ('partner_settlement_settings', 'partner_settlement_settings_id_seq'),
            ('physical_beds', 'physical_beds_id_seq'),
            ('sales_inventory_movements', 'sales_inventory_movements_id_seq'),
            ('sales_orchid_group_snapshots', 'sales_orchid_group_snapshots_id_seq'),
            ('sales_slip_item_allocations', 'sales_slip_item_allocations_id_seq'),
            ('sales_slip_items', 'sales_slip_items_id_seq'),
            ('sales_slips', 'sales_slips_id_seq'),
            ('varieties', 'varieties_id_seq'),
            ('work_applied_effects', 'work_applied_effects_id_seq'),
            ('work_effect_orchid_groups', 'work_effect_orchid_groups_id_seq'),
            ('work_operation_corrections', 'work_operation_corrections_id_seq'),
            ('work_operation_targets', 'work_operation_targets_id_seq'),
            ('work_operations', 'work_operations_id_seq'),
            ('work_target_executions', 'work_target_executions_id_seq'),
            ('work_types', 'work_types_id_seq')
        ) AS entity_sequences(table_name, sequence_name)
    LOOP
        EXECUTE format('SELECT COALESCE(MAX(id), 0) FROM %I', sequence_row.table_name)
            INTO table_max_id;
        EXECUTE format('SELECT last_value, is_called FROM %I', sequence_row.sequence_name)
            INTO sequence_last_value, sequence_is_called;

        restart_value := GREATEST(table_max_id, sequence_last_value);

        EXECUTE format(
            'ALTER SEQUENCE %I INCREMENT BY 50 CACHE 1',
            sequence_row.sequence_name
        );

        IF table_max_id = 0 AND NOT sequence_is_called THEN
            EXECUTE 'SELECT setval($1::regclass, 1, false)'
                USING sequence_row.sequence_name;
        ELSE
            EXECUTE 'SELECT setval($1::regclass, $2, true)'
                USING sequence_row.sequence_name, restart_value;
        END IF;
    END LOOP;
END $$;
