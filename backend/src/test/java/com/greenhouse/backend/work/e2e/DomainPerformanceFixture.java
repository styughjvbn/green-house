package com.greenhouse.backend.work.e2e;

import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic historical shapes for isolated measurement; never an operational data import. */
final class DomainPerformanceFixture {
  private static final long GROUP_BASE = 97000000, MUTATION_BASE = 98000000, WORK_ID = 99000000;
  private static final LocalDate DATE = LocalDate.of(2026, 8, 20);
  private final JdbcTemplate jdbc;
  private final WorkTestDataSeeder seeder;
  private final BusinessPartnerRepository partners;
  private final OrchidGroupLedgerTestFixture ledgerFixture;

  DomainPerformanceFixture(
      JdbcTemplate jdbc,
      WorkTestDataSeeder seeder,
      BusinessPartnerRepository partners,
      OrchidGroupLedgerTestFixture ledgerFixture) {
    this.jdbc = jdbc;
    this.seeder = seeder;
    this.partners = partners;
    this.ledgerFixture = ledgerFixture;
  }

  void reset() {
    seeder.resetKeepingSequences();
    jdbc.execute(
        "TRUNCATE auction_shipments, auction_settlements, partner_payment_events, partner_balance_summaries, partner_settlement_settings CONTINUE IDENTITY CASCADE");
  }

  void settlement(int keys, int resultsPerKey) {
    reset();
    long house =
        partners
            .saveAndFlush(
                new BusinessPartner("측정 경매장", PartnerType.AUCTION_HOUSE, null, null, null, null))
            .getId();
    int count = Math.multiplyExact(keys, resultsPerKey);
    jdbc.update(
        """
        insert into auction_shipments (id, auction_house_id, shipment_date, status, created_at, updated_at)
        select ?+n, ?, date '2045-01-01' + ((n-1)/?), 'SHIPPED', now(), now()
        from generate_series(1,?) n
        """,
        GROUP_BASE,
        house,
        resultsPerKey,
        count);
    jdbc.execute(
        """
        insert into auction_shipment_lots (id,shipment_id,variety_name,item_name,shipped_quantity,sold_quantity,waiting_quantity,returned_quantity,current_status,version,created_at,updated_at)
        select id,id,'측정 품종','난',1,1,0,0,'SOLD',0,now(),now() from auction_shipments
        """);
    jdbc.execute(
        """
        insert into auction_attempts (id,shipment_lot_id,attempt_no,attempt_status,auction_date,created_at,updated_at)
        select id,id,1,'SOLD',shipment_date,now(),now() from auction_shipments
        """);
    jdbc.execute(
        """
        insert into auction_result_lines (id,auction_attempt_id,auction_date,quantity,unit_price,amount,inspection_status,created_at,updated_at)
        select id,id,auction_date,1,1000,1000,'NORMAL',now(),now() from auction_attempts
        """);
    jdbc.execute("ANALYZE auction_result_lines");
  }

  void ledger(int count, int revisions, boolean workReferences, boolean errors) {
    reset();
    long first = seeder.seedContractScenario().orchidGroupId();
    jdbc.update("update orchid_groups set quantity=0 where id=?", first);
    var original =
        jdbc.queryForObject(
            "select to_jsonb(g)::text from orchid_groups g where id=?", String.class, first);
    jdbc.update(
        """
        insert into orchid_groups
        select (jsonb_populate_record(null::orchid_groups, ?::jsonb || jsonb_build_object('id',?+n,'sort_order',n+1))).*
        from generate_series(1,?) n
        """,
        original,
        GROUP_BASE,
        count - 1);
    UUID correlation = UUID.randomUUID();
    ledgerFixture.seedBaseline(correlation, DATE, "1.0.0");
    long entries = Math.multiplyExact(count, revisions);
    jdbc.update(
        """
        insert into orchid_group_mutations (id,mutation_type,source_domain,source_type,source_reference_id,source_operation_key,correlation_id,command_fingerprint,occurred_at,recorded_at,effective_business_date,schema_version)
        select ?+n, ?, ?, ?, (?+n)::text, n::text, ?::uuid, repeat('a',64), now(),now(),?,1
        from generate_series(1,?) n
        """,
        MUTATION_BASE,
        workReferences ? "CORRECTION" : "UPDATE_DETAILS",
        workReferences ? "WORK" : "FARM",
        workReferences ? "WORK_CORRECTION" : "DOMAIN_BENCHMARK",
        MUTATION_BASE,
        correlation.toString(),
        DATE,
        entries);
    jdbc.update(
        """
        with baseline as (
          select orchid_group_id, after_state, row_number() over(order by orchid_group_id) ordinal
          from orchid_group_mutation_entries where entry_kind='BASELINE'
        )
        insert into orchid_group_mutation_entries (id,mutation_id,orchid_group_id,entry_kind,role,state_revision_before,state_revision_after,before_state,after_state)
        select ?+(b.ordinal-1)*?+r, ?+(b.ordinal-1)*?+r, b.orchid_group_id,'CHANGE','AFFECTED',r-1,r,
          case when ? and r=1 then jsonb_set(b.after_state,'{memo}','"측정 오류"'::jsonb) else b.after_state end,
          b.after_state
        from baseline b cross join generate_series(1,?) r
        """,
        MUTATION_BASE,
        revisions,
        MUTATION_BASE,
        revisions,
        errors,
        revisions);
    jdbc.update("update orchid_groups set state_revision=?", revisions);
    if (workReferences) workReferences(correlation);
    jdbc.execute("ANALYZE orchid_groups");
    jdbc.execute("ANALYZE orchid_group_mutation_entries");
    jdbc.execute("ANALYZE orchid_group_mutations");
    jdbc.execute("ANALYZE work_operation_corrections");
  }

  private void workReferences(UUID correlation) {
    jdbc.update(
        """
        insert into work_operations (id,work_type_id,title,status,planned_start_date,source_scope_type,target_snapshot_at,worker,version,created_at,updated_at)
        select ?,id,'측정 작업','COMPLETED',?,'MANUAL_SELECTION',now(),'측정',0,now(),now()
        from work_types where code='MOVEMENT'
        """,
        WORK_ID,
        DATE);
    jdbc.update(
        """
        insert into work_operation_targets (work_operation_id,orchid_group_id,target_reference_type,inclusion_source,included_at,variety_id_snapshot,variety_name_snapshot,age_year_snapshot,pot_size_code_snapshot,pot_size_snapshot,quantity_snapshot,location_snapshot,created_at)
        select ?,id,'ORCHID_GROUP','MANUAL_ADDITION',now(),variety_id,variety_name,age_year,pot_size_code,pot_size,quantity,'{}'::jsonb,now() from orchid_groups
        """,
        WORK_ID);
    jdbc.update(
        """
        insert into work_operation_corrections (id,original_work_operation_id,reason,created_at,result_details,mutation_id,correlation_id)
        select e.mutation_id,?,'측정 보정',now(),
          jsonb_build_object('adjustments',jsonb_build_array(jsonb_build_object(
            'orchidGroupId',e.orchid_group_id,'beforeQuantity',0,'afterQuantity',0,'beforeStatus','정상','afterStatus','정상'))),
          e.mutation_id,?::uuid
        from orchid_group_mutation_entries e where entry_kind='CHANGE'
        """,
        WORK_ID,
        correlation.toString());
  }
}
