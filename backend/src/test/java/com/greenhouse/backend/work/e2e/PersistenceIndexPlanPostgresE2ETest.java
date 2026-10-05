package com.greenhouse.backend.work.e2e;

import static com.greenhouse.backend.work.e2e.PersistencePlanFixtures.BASE;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.greenhouse.backend.auction.repository.AuctionShipmentRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.transformation.OrchidGroupLineageRepository;
import com.greenhouse.backend.sales.domain.SalesInventoryMovementType;
import com.greenhouse.backend.sales.repository.SalesInventoryMovementRepository;
import com.greenhouse.backend.sales.repository.SalesSlipItemAllocationRepository;
import com.greenhouse.backend.sales.repository.SalesSlipRepository;
import com.greenhouse.backend.settlement.repository.AuctionSettlementRepository;
import com.greenhouse.backend.settlement.repository.PartnerPaymentEventRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Import(QueryShapeCapture.Configuration.class)
class PersistenceIndexPlanPostgresE2ETest extends WorkE2ETestBase {
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate transactions;
  @Autowired QueryShapeCapture capture;
  @Autowired OrchidGroupRepository groups;
  @Autowired InboundRecordRepository inbound;
  @Autowired SalesSlipRepository sales;
  @Autowired SalesSlipItemAllocationRepository allocations;
  @Autowired SalesInventoryMovementRepository movements;
  @Autowired AuctionSettlementRepository settlements;
  @Autowired PartnerPaymentEventRepository payments;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired OrchidGroupLineageRepository lineage;

  @ParameterizedTest
  @ValueSource(ints = {1000, 50000})
  void measuresRealRepositorySqlBeforeAndAfterIndexesWithoutForcingPlanner(int roots)
      throws Exception {
    var zones = new PersistencePlanFixtures(jdbc).seed(roots);
    var definitions =
        jdbc.queryForList(
            "select indexname, indexdef, pg_relation_size((quote_ident(schemaname)||'.'||quote_ident(indexname))::regclass) as index_bytes from pg_indexes where schemaname='public' and indexname in ("
                + "'idx_orchid_groups_zone_sort','idx_orchid_groups_active_zone','idx_orchid_groups_inbound',"
                + "'idx_sales_items_slip','idx_sales_allocations_item','idx_sales_allocations_group',"
                + "'idx_sales_movements_slip_type','idx_sales_movements_group','idx_settlement_lines_parent',"
                + "'idx_settlement_lines_lot','idx_sales_slips_date','idx_sales_slips_partner_date',"
                + "'idx_settlements_date','idx_inbound_records_date','idx_payment_events_date',"
                + "'idx_auction_shipments_date_id','idx_lineage_mutation_result') order by indexname");
    assertThat(definitions).hasSize(17);
    // Validate the operator inventory on actual PostgreSQL, outside any write transaction.
    jdbc.execute(Files.readString(Path.of("../scripts/performance/inspect-backend-indexes.sql")));
    var scenarios = scenarios(zones);
    List<Map<String, Object>> measurements =
        transactions.execute(
            tx -> {
              // Only the disposable test DB: DDL rollback restores release indexes even on
              // assertion failure.
              tx.setRollbackOnly();
              for (var definition : definitions)
                jdbc.execute("DROP INDEX " + definition.get("indexname"));
              var before = measure(scenarios);
              for (var definition : definitions)
                jdbc.execute(definition.get("indexdef").toString());
              var after = measure(scenarios);
              var results = new ArrayList<Map<String, Object>>();
              for (int i = 0; i < scenarios.size(); i++) {
                var oldPlan = before.get(i);
                var newPlan = after.get(i);
                assertThat(newPlan.path("Plan").path("Actual Rows").asLong())
                    .as(scenarios.get(i).name())
                    .isEqualTo(oldPlan.path("Plan").path("Actual Rows").asLong());
                var result = new LinkedHashMap<String, Object>();
                result.put("name", scenarios.get(i).name());
                result.put("sql", scenarios.get(i).sql());
                result.put("before", oldPlan);
                result.put("after", newPlan);
                results.add(result);
              }
              return results;
            });
    var output = Path.of("build/work-query-plans/persistence-" + roots + ".json");
    Files.createDirectories(output.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            output.toFile(),
            Map.of("rootRows", roots, "indexes", definitions, "scenarios", measurements));
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where schemaname='public'", String.class))
        .containsAll(definitions.stream().map(d -> d.get("indexname").toString()).toList());
    assertPlanBudgets(roots, measurements);
  }

  private List<Scenario> scenarios(List<Long> zones) {
    var cases = new ArrayList<Scenario>();
    cases.add(
        repository(
            "placement-selective",
            () -> groups.findActivePlacements(List.of(zones.get(1))),
            zones.get(1)));
    cases.add(
        repository(
            "placement-broad",
            () -> groups.findActivePlacements(List.of(zones.getFirst())),
            zones.getFirst()));
    cases.add(
        repository(
            "zone-max-sort",
            () -> groups.findMaxSortOrderByBedZoneId(zones.getFirst()),
            zones.getFirst()));
    cases.add(
        repository(
            "inbound-results",
            () -> groups.findInboundResultDetailsByInboundRecordIdIn(List.of(BASE + 5)),
            BASE + 5));
    cases.add(
        repository(
            "sales-detail",
            () -> sales.findItemsWithAllocationsBySalesSlipId(BASE + 10),
            BASE + 10));
    cases.add(
        repository(
            "sales-snapshots",
            () -> allocations.findAllWithSnapshotsBySalesSlipIdIn(List.of(BASE + 10)),
            BASE + 10));
    cases.add(
        repository(
            "reservation-sum",
            () -> allocations.sumDraftReservationsByOrchidGroupIdIn(List.of(BASE + 10), "작성중"),
            BASE + 10,
            "작성중"));
    cases.add(
        repository(
            "movement-by-slip",
            () ->
                movements.findBySalesSlipIdAndChangeType(
                    BASE + 10, SalesInventoryMovementType.SALES_OUTBOUND),
            BASE + 10,
            "SALES_OUTBOUND"));
    cases.add(
        repository(
            "movement-by-group",
            () -> movements.countByOrchidGroupIdIn(List.of(BASE + 10)),
            BASE + 10));
    cases.add(
        repository(
            "settlement-detail", () -> settlements.findWithDetailsById(BASE + 10), BASE + 10));
    cases.add(
        repository(
            "settled-lot", () -> settlements.findSettledLotIds(List.of(BASE + 20)), BASE + 20));
    cases.add(
        repository(
            "sales-page-all",
            () ->
                sales.searchPage(
                    null, null, null, null, null, null, List.of(), PageRequest.of(0, 20)),
            0,
            20));
    cases.add(
        repository(
            "sales-page-partner",
            () ->
                sales.searchPage(
                    BASE + 1, null, null, null, null, null, List.of(), PageRequest.of(0, 20)),
            BASE + 1,
            0,
            20));
    cases.add(
        repository(
            "settlement-page-all",
            () -> settlements.search(null, null, null, null, PageRequest.of(0, 20)),
            nil(Types.BIGINT),
            nil(Types.BIGINT),
            nil(Types.DATE),
            nil(Types.DATE),
            nil(Types.DATE),
            nil(Types.DATE),
            nil(Types.VARCHAR),
            nil(Types.VARCHAR),
            20));
    cases.add(
        repository(
            "inbound-page-all",
            () -> inbound.search(null, null, null, null, "", PageRequest.of(0, 20)),
            nil(Types.DATE),
            nil(Types.DATE),
            nil(Types.DATE),
            nil(Types.DATE),
            nil(Types.VARCHAR),
            nil(Types.VARCHAR),
            nil(Types.VARCHAR),
            nil(Types.VARCHAR),
            "",
            "",
            20));
    cases.add(
        repository(
            "payment-page-all",
            () -> payments.search(null, null, null, null, PageRequest.of(0, 20)),
            nil(Types.BIGINT),
            nil(Types.BIGINT),
            nil(Types.VARCHAR),
            nil(Types.VARCHAR),
            nil(Types.BIGINT),
            nil(Types.BIGINT),
            nil(Types.VARCHAR),
            nil(Types.VARCHAR),
            20));
    cases.add(
        repository(
            "shipment-page-all", () -> shipments.findIdsNewestFirst(PageRequest.of(0, 20)), 20));
    cases.add(
        repository(
            "graph-lineage-label",
            () -> lineage.findGraphLineageTypes(List.of(BASE + 1), List.of(BASE + 1)),
            BASE + 1,
            BASE + 1));
    var visibleIds = LongStream.rangeClosed(BASE + 1, BASE + 101).boxed().toList();
    var graphArguments = new ArrayList<Object>(visibleIds);
    graphArguments.addAll(visibleIds);
    cases.add(
        repository(
            "graph-lineage-many-labels",
            () -> {
              var labels = lineage.findGraphLineageTypes(visibleIds, visibleIds);
              assertThat(labels).hasSize(101);
              assertThat(labels.stream().map(row -> row.mutationId()).toList())
                  .containsExactlyInAnyOrderElementsOf(visibleIds);
              return labels;
            },
            graphArguments.toArray()));
    try {
      cases.add(
          new Scenario(
              "graph-lineage-legacy",
              Files.readString(Path.of("src/test/resources/performance/legacy-graph-lineage.sql")),
              new Object[] {BASE + 1, BASE + 1}));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
    // Component predicates: these do not claim to explain the complete search/calendar API.
    cases.add(
        new Scenario(
            "partner-contains-selective",
            "select id from business_partners where lower(name) like '%partner 99%' order by id",
            new Object[0]));
    cases.add(
        new Scenario(
            "partner-contains-broad",
            "select id from business_partners where lower(name) like '%partner%' order by id",
            new Object[0]));
    cases.add(
        new Scenario(
            "sales-contains-or",
            "select id from sales_slips where lower(slip_number) like '%plan%' or lower(memo) like '%xx%' order by sale_date desc,id desc limit 20",
            new Object[0]));
    cases.add(
        new Scenario(
            "auction-status-broad",
            "select id from auction_shipment_lots where current_status='SOLD' order by id desc limit 20",
            new Object[0]));
    cases.add(
        new Scenario(
            "work-calendar-range",
            "select id from work_operations where planned_start_date between date '2040-01-01' and date '2040-01-02' order by planned_start_date,id limit 1001",
            new Object[0]));
    cases.add(
        new Scenario(
            "native-quantity-write",
            "update orchid_groups set quantity=quantity+1, version=version+1 where id in (select id from orchid_groups where quantity>0 order by id limit 200) returning id",
            new Object[0]));
    return cases;
  }

  private Scenario repository(String name, Supplier<?> query, Object... args) {
    return transactions.execute(
        tx -> {
          capture.start();
          var result = query.get();
          var sql = capture.stop();
          assertThat(result).isNotNull();
          if (result instanceof List<?> rows) assertThat(rows).as(name).isNotEmpty();
          if (result instanceof Optional<?> row) assertThat(row).as(name).isPresent();
          if (result instanceof Page<?> page) assertThat(page.getContent()).as(name).isNotEmpty();
          assertThat(sql).isNotEmpty();
          String first = sql.getFirst();
          assertThat(first.chars().filter(c -> c == '?').count())
              .as(name + " bindings: " + first)
              .isEqualTo(args.length);
          return new Scenario(name, first, args);
        });
  }

  private List<JsonNode> measure(List<Scenario> scenarios) {
    return scenarios.stream()
        .map(
            s -> {
              boolean writeSample = s.name().equals("native-quantity-write");
              if (writeSample) jdbc.execute("SAVEPOINT write_sample");
              var json =
                  jdbc.queryForObject(
                      "EXPLAIN (ANALYZE, BUFFERS, WAL, FORMAT JSON) " + s.sql(),
                      String.class,
                      s.args());
              if (writeSample) {
                jdbc.execute("ROLLBACK TO SAVEPOINT write_sample");
                jdbc.execute("RELEASE SAVEPOINT write_sample");
              }
              try {
                return objectMapper.readTree(json).get(0);
              } catch (Exception ex) {
                throw new IllegalStateException(s.name(), ex);
              }
            })
        .toList();
  }

  private SqlParameterValue nil(int type) {
    return new SqlParameterValue(type, null);
  }

  private void assertPlanBudgets(int roots, List<Map<String, Object>> measurements) {
    var byName = new LinkedHashMap<String, Map<String, Object>>();
    measurements.forEach(m -> byName.put(m.get("name").toString(), m));
    for (var name : List.of("graph-lineage-label", "graph-lineage-many-labels")) {
      var plan = ((JsonNode) byName.get(name).get("after")).path("Plan");
      int visibleEntries = name.equals("graph-lineage-label") ? 1 : 101;
      assertThat(plan.path("Actual Rows").asInt()).as(name).isEqualTo(visibleEntries);
      assertThat(sharedBlocks(plan))
          .as(name + " buffer budget")
          .isLessThanOrEqualTo(20L * visibleEntries + 100);
      assertSubplanLoops(plan, visibleEntries);
    }
    if (roots < 50000) return; // Small fixtures may legitimately use sequential scans.
    for (var name :
        List.of(
            "zone-max-sort",
            "inbound-results",
            "sales-detail",
            "sales-snapshots",
            "reservation-sum",
            "movement-by-slip",
            "movement-by-group",
            "settlement-detail",
            "settled-lot",
            "sales-page-all",
            "sales-page-partner",
            "settlement-page-all",
            "inbound-page-all",
            "payment-page-all",
            "shipment-page-all")) {
      var measurement = byName.get(name);
      var before = ((JsonNode) measurement.get("before")).path("Plan");
      var after = ((JsonNode) measurement.get("after")).path("Plan");
      assertThat(sharedBlocks(after)).as(name + " selective buffer budget").isLessThan(150);
      assertThat(sharedBlocks(after) * 2)
          .as(name + " vs unindexed fixture")
          .isLessThan(sharedBlocks(before));
    }
    var placement = byName.get("placement-selective");
    assertThat(sharedBlocks(((JsonNode) placement.get("after")).path("Plan")) * 2)
        .as("selective zone vs unindexed fixture")
        .isLessThan(sharedBlocks(((JsonNode) placement.get("before")).path("Plan")));
    var current = ((JsonNode) byName.get("graph-lineage-label").get("after")).path("Plan");
    var legacy = ((JsonNode) byName.get("graph-lineage-legacy").get("after")).path("Plan");
    assertThat(sharedBlocks(current) * 10)
        .as("Entry-driven MIN vs indexed legacy query")
        .isLessThan(sharedBlocks(legacy));
  }

  private long sharedBlocks(JsonNode plan) {
    // Parent counters include children: do not sum the tree and double-count accesses.
    return plan.path("Shared Hit Blocks").asLong() + plan.path("Shared Read Blocks").asLong();
  }

  private void assertSubplanLoops(JsonNode plan, int visibleEntries) {
    // A hash join may evaluate the MIN expression twice per Entry (hash key and join output).
    // Keep a linear budget; do not require a particular join strategy.
    if (plan.path("Subplan Name").asText().startsWith("SubPlan"))
      assertThat(plan.path("Actual Loops").asLong())
          .as("MIN loops per visible Entry")
          .isLessThanOrEqualTo(2L * visibleEntries);
    plan.path("Plans").forEach(child -> assertSubplanLoops(child, visibleEntries));
  }

  private record Scenario(String name, String sql, Object[] args) {}
}
