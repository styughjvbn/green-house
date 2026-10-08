package com.greenhouse.backend.work.e2e;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Native fixtures model cardinality and skew, not application write throughput. */
final class PersistencePlanFixtures {
  static final long BASE = 90000000L;
  private final JdbcTemplate jdbc;

  PersistencePlanFixtures(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<Long> seed(int roots) {
    jdbc.execute(
        "TRUNCATE business_partners, varieties, orchid_groups, inbound_records, sales_slips, auction_shipments, auction_proceeds, partner_payment_events, orchid_group_mutations, orchid_group_lineage CONTINUE IDENTITY CASCADE");
    var zones = jdbc.queryForList("select id from bed_zones order by id limit 20", Long.class);
    if (zones.size() != 20) throw new IllegalStateException("20 fixture zones required");
    jdbc.update(
        """
        insert into business_partners (id, name, owner_name, partner_type, is_active, created_at, updated_at)
        select 90000000+n, 'Plan partner '||n, 'Plan contact '||n, 'AUCTION_HOUSE', true, now(), now()
        from generate_series(1,?) n
        """,
        roots);
    jdbc.update(
        """
        insert into varieties (id, code, genus, name, default_pot_size, sale_enabled, is_active, created_at, updated_at)
        values (90000001, 'PLAN-1', '팔레놉시스', 'Plan variety', '3.5치', true, true, now(), now())
        """);
    jdbc.update(
        """
        insert into inbound_records (id, created_at, updated_at, inbound_date, inbound_type, variety_id, status, estimated_quantity)
        select 90000000+n, now(), now(), date '2040-01-01' + n%365, 'PRODUCT_POT', 90000001, 'PLACED', 10
        from generate_series(1,?) n
        """,
        roots);
    // Half of the rows are in one zone; the rest use 19 zones. Only 20% remain active.
    String zoneArray = zones.toString().replace('[', '{').replace(']', '}');
    jdbc.update(
        """
        insert into orchid_groups (id, created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, reserved_quantity, sort_order, status, variety_name,
          bed_zone_id, variety_id, inbound_record_id, start_position, end_position, memo, version)
        select 90000000+n, now(), now(), 2, '팔레놉시스', 'POT', '3.5치', 'POT_3_5',
          case when n%5=0 then 10 else 0 end, case when n%10=0 then 2 else 0 end,
          n, '정상', 'Plan variety', (?::bigint[])[case when n%100<50 then 1 else 2+n%19 end],
          90000001, 90000000+n, 0, 1, repeat('x',100), 0 from generate_series(1,?) n
        """,
        zoneArray, roots);
    jdbc.update(
        """
        insert into sales_slips (id, slip_number, sale_date, sales_type, partner_id, payment_status,
          sales_status, total_amount, paid_amount, remaining_amount, version, memo, created_at, updated_at)
        select 90000000+n, 'PLAN-'||n, date '2040-01-01'+n%365, 'DIRECT', 90000001+n%100,
          '미입금', case when n%10=0 then '작성중' else '출고 완료' end, 20, 0, 20, 0, repeat('x',100), now(), now()
        from generate_series(1,?) n
        """,
        roots);
    jdbc.update(
        """
        insert into sales_slip_items (id, sales_slip_id, item_name, quantity, unit_price, amount)
        select 90000000+2*n+r, 90000000+n, 'Plan item', 1, 10, 10
        from generate_series(1,?) n cross join generate_series(0,1) r
        """,
        roots);
    jdbc.update(
        """
        insert into sales_slip_item_allocations (id, created_at, updated_at, sales_slip_item_id, orchid_group_id, allocated_quantity)
        select id, now(), now(), id, sales_slip_id, 1 from sales_slip_items
        """);
    jdbc.update(
        """
        insert into sales_inventory_movements (id, created_at, updated_at, orchid_group_id,
          sales_slip_id, sales_slip_item_id, change_type, quantity_delta)
        select id, now(), now(), sales_slip_id, sales_slip_id, id, 'SALES_OUTBOUND', -1 from sales_slip_items
        """);
    jdbc.update(
        """
        insert into auction_shipments (id, created_at, updated_at, shipment_date, status, auction_house_id)
        select 90000000+n, now(), now(), date '2040-01-01'+n%365, 'SHIPPED', 90000001+n%100 from generate_series(1,?) n
        """,
        roots);
    jdbc.update(
        """
        insert into auction_shipment_lots (id, created_at, updated_at, current_status, item_name,
          shipped_quantity, sold_quantity, waiting_quantity, returned_quantity, variety_name, shipment_id, version)
        select id, now(), now(), 'SOLD', 'Plan lot', 1, 1, 0, 0, 'Plan variety', sales_slip_id, 0 from sales_slip_items
        """);
    jdbc.update(
        """
        insert into auction_attempts (id, created_at, updated_at, attempt_no, attempt_status, auction_date, shipment_lot_id)
        select id, now(), now(), 1, 'SOLD', date '2040-01-01', id from auction_shipment_lots
        """);
    jdbc.update(
        """
        insert into auction_result_lines (id, created_at, updated_at, amount, auction_date,
          inspection_status, quantity, unit_price, auction_attempt_id)
        select id, now(), now(), 10, date '2040-01-01', 'NORMAL', 1, 10, id from auction_attempts
        """);
    jdbc.update(
        """
        insert into auction_proceeds (id, created_at, updated_at, auction_house_id,
          source_reference, reported_gross_amount, receivable_amount, matching_confirmed, confirmed_at, confirmed_by, version)
        select 90000000+n, now(), now(), 90000001+n%100,'제공 지급 자료',20,20,true,now(),'확인자',0 from generate_series(1,?) n
        """,
        roots);
    jdbc.update(
        """
        insert into auction_proceeds_results (auction_result_line_id, auction_proceeds_id)
        select id,sales_slip_id from sales_slip_items
        """);
    jdbc.update(
        """
        insert into partner_payment_events (id, created_at, updated_at, partner_id, event_date, event_type,
          amount, unapplied_amount, status)
        select 90000000+n, now(), now(), 90000001+n%100, date '2040-01-01'+n%365,
          'PAYMENT_RECEIVED', 10, 10, 'UNAPPLIED' from generate_series(1,?) n
        """,
        roots);
    jdbc.update(
        """
        insert into work_operations (id, work_type_id, title, status, planned_start_date, source_scope_type,
          target_snapshot_at, version, created_at, updated_at)
        select 90000000+n, (select id from work_types where code='PESTICIDE'), 'Plan work', 'PLANNED',
          date '2040-01-01'+n%365, 'NONE', now(), 0, now(), now() from generate_series(1,?) n
        """,
        roots);
    jdbc.update(
        """
        insert into orchid_group_mutations (id, mutation_type, source_domain, source_type,
          source_reference_id, source_operation_key, correlation_id, command_fingerprint,
          occurred_at, recorded_at, effective_business_date, schema_version)
        select 90000000+n, 'TRANSFORM', 'FARM', 'PLAN', n::text, n::text,
          '00000000-0000-0000-0000-000000000035'::uuid, repeat('a',64), now(), now(), date '2040-01-01', 1
        from generate_series(1,101) n
        """);
    jdbc.update(
        """
        insert into orchid_group_mutation_entries (id, mutation_id, orchid_group_id, entry_kind,
          role, state_revision_after, after_state)
        select 90000000+n, 90000000+n, 90000000+n, 'CREATE', 'RESULT', 1, '{}'::jsonb
        from generate_series(1,101) n
        """);
    // Unrelated lineage comes first; 100 other pairs exercise real key selectivity.
    jdbc.update(
        """
        insert into orchid_group_lineage (source_orchid_group_id, result_orchid_group_id,
          relation_type, work_operation_id, source_quantity, result_quantity, mutation_id, created_at)
        select id, 90000002+(id-90000000)%100, 'SPLIT_TO', 90000002+(id-90000000)%100,
          1, 1, 90000002+(id-90000000)%100, now()
        from orchid_groups where id <> 90000002+(id-90000000)%100 and id <= 90005001 order by id
        """);
    jdbc.update(
        """
        insert into orchid_group_lineage (source_orchid_group_id, result_orchid_group_id,
          relation_type, work_operation_id, source_quantity, result_quantity, mutation_id, created_at)
        select id, 90000001, 'SPLIT_TO', 90000001, 1, 1, 90000001, now()
        from orchid_groups where id > 90000001 and id <= 90005001
        """);
    for (var table :
        List.of(
            "orchid_groups",
            "inbound_records",
            "sales_slips",
            "sales_slip_items",
            "sales_slip_item_allocations",
            "sales_inventory_movements",
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines",
            "auction_proceeds",
            "auction_proceeds_results",
            "partner_payment_events",
            "business_partners",
            "work_operations",
            "orchid_group_lineage",
            "orchid_group_mutation_entries")) jdbc.execute("ANALYZE " + table);
    return zones;
  }
}
