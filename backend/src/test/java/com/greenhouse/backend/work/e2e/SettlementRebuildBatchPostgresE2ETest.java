package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import com.greenhouse.backend.auction.application.AuctionDataReader;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.settlement.application.AuctionSettlementRebuildService;
import com.greenhouse.backend.settlement.application.ExpectedPaymentDateCalculator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Import(QueryShapeCapture.Configuration.class)
class SettlementRebuildBatchPostgresE2ETest extends WorkE2ETestBase {
  private static final long BASE = 91000000;
  private static final LocalDate DATE = LocalDate.of(2045, 1, 1);
  @Autowired JdbcTemplate jdbc;
  @Autowired BusinessPartnerRepository partners;
  @Autowired AuctionSettlementRebuildService rebuild;
  @Autowired AuctionDataReader auctionReader;
  @Autowired QueryShapeCapture capture;
  @Autowired TransactionTemplate transactions;
  @Autowired EntityManager entityManager;
  @Autowired EntityManagerFactory emf;
  @MockitoSpyBean ExpectedPaymentDateCalculator calculator;
  private long house;

  @BeforeEach
  void setup() {
    reset(calculator);
    jdbc.execute(
        "TRUNCATE auction_shipments, auction_settlements, partner_payment_events, partner_balance_summaries CONTINUE IDENTITY CASCADE");
    house =
        partners
            .saveAndFlush(
                new BusinessPartner("정산 처리 단위", PartnerType.AUCTION_HOUSE, null, null, null, null))
            .getId();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 50, 501})
  void commitsEachKeyInAFreshTransactionAndBoundsManagedEntities(int keys) {
    seed(keys, false);
    var transactionIds = new HashSet<Long>();
    var largestContext = new AtomicInteger();
    doAnswer(
            call -> {
              transactionIds.add(jdbc.queryForObject("select txid_current()", Long.class));
              largestContext.accumulateAndGet(
                  entityManager.unwrap(Session.class).getStatistics().getEntityCount(), Math::max);
              return call.callRealMethod();
            })
        .when(calculator)
        .calculate(any(), any());
    assertThat(rebuild.rebuildExistingResults()).isEqualTo(keys);
    assertThat(transactionIds).hasSize(keys);
    assertThat(largestContext.get()).isLessThanOrEqualTo(5);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlements", Integer.class))
        .isEqualTo(keys);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
        .isEqualTo(keys);
    assertThat(jdbc.queryForObject("select sum(gross_amount) from auction_settlements", Long.class))
        .isEqualTo(keys * 1000L);
    assertThat(rebuild.rebuildExistingResults()).isZero();
  }

  @Test
  void keepsOneLargeSettlementAtomicAcrossCandidatePages() {
    seed(1501, true);
    assertThat(rebuild.rebuildExistingResults()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlements", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
        .isEqualTo(1501);
    assertThat(jdbc.queryForObject("select gross_amount from auction_settlements", Long.class))
        .isEqualTo(1501000);
    assertThat(rebuild.rebuildExistingResults()).isZero();
  }

  @Test
  void aLateDatabaseFailureKeepsCommittedKeysAndRestartOnlyProcessesRemainingKeys() {
    seed(3, false);
    var reachedSecondKey = new AtomicBoolean();
    doAnswer(
            call -> {
              if (DATE.plusDays(2).equals(call.getArgument(1))) reachedSecondKey.set(true);
              return call.callRealMethod();
            })
        .when(calculator)
        .calculate(any(), any());
    jdbc.execute(
        "alter table auction_settlements add constraint be036_late_failure check (auction_date <> date '2045-01-03')");
    try {
      assertThatThrownBy(rebuild::rebuildExistingResults)
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasStackTraceContaining("be036_late_failure");
      assertThat(reachedSecondKey).isTrue();
      assertThat(jdbc.queryForList("select auction_date from auction_settlements", LocalDate.class))
          .containsExactly(DATE.plusDays(1));
      assertThat(
              jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
          .isEqualTo(1);
    } finally {
      jdbc.execute("alter table auction_settlements drop constraint be036_late_failure");
    }
    assertThat(rebuild.rebuildExistingResults()).isEqualTo(2);
    assertThat(rebuild.rebuildExistingResults()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void loadsOnlyExactKeysWhenNewResultsAreAtOppositeEndsOfAWideDateRange() {
    seed(501, false);
    rebuild.rebuildExistingResults();
    append(BASE + 502, DATE.plusDays(1));
    append(BASE + 503, DATE.plusDays(501));
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    assertThat(rebuild.rebuildExistingResults()).isEqualTo(2);
    assertThat(
            stats
                .getEntityStatistics("com.greenhouse.backend.settlement.domain.AuctionSettlement")
                .getLoadCount())
        .isEqualTo(2);
    assertThat(
            stats
                .getEntityStatistics(
                    "com.greenhouse.backend.settlement.domain.AuctionSettlementLine")
                .getLoadCount())
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
        .isEqualTo(503);
  }

  @Test
  void capturesAFiniteUpperIdAndLeavesLaterResultsForTheNextRun() {
    seed(2, false);
    var inserted = new AtomicBoolean();
    doAnswer(
            call -> {
              if (inserted.compareAndSet(false, true)) append(BASE + 3, DATE.plusDays(1));
              return call.callRealMethod();
            })
        .when(calculator)
        .calculate(any(), any());
    assertThat(rebuild.rebuildExistingResults()).isEqualTo(2);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
        .isEqualTo(2);
    assertThat(rebuild.rebuildExistingResults()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlement_lines", Integer.class))
        .isEqualTo(3);
  }

  @Test
  void rejectsAnOuterTransactionInsteadOfPretendingToCommitEachKey() {
    seed(1, false);
    assertThatThrownBy(() -> transactions.execute(tx -> rebuild.rebuildExistingResults()))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(jdbc.queryForObject("select count(*) from auction_settlements", Integer.class))
        .isZero();
  }

  @Test
  void exactDateLookupAvoidsScanningAllResultsForEveryCommitUnit() throws Exception {
    seed(50000, false);
    for (var table :
        List.of(
            "auction_shipments",
            "auction_shipment_lots",
            "auction_attempts",
            "auction_result_lines")) jdbc.execute("ANALYZE " + table);
    var date = DATE.plusDays(25000);
    capture.start();
    assertThat(auctionReader.getSoldResultLinesUpTo(house, date, BASE + 50000)).hasSize(1);
    var sql = capture.stop().getFirst();
    assertThat(sql.chars().filter(c -> c == '?').count()).isEqualTo(3);
    var plans =
        transactions.execute(
            tx -> {
              tx.setRollbackOnly();
              var indexDefinition =
                  jdbc.queryForObject(
                      "select indexdef from pg_indexes where indexname='idx_auction_results_sold_date_id'",
                      String.class);
              jdbc.execute("drop index idx_auction_results_sold_date_id");
              var before = explain(sql, date, house, BASE + 50000);
              jdbc.execute(indexDefinition);
              var after = explain(sql, date, house, BASE + 50000);
              return Map.of("sql", sql, "before", before, "after", after);
            });
    var output = Path.of("build/work-query-plans/settlement-rebuild-date.json");
    Files.createDirectories(output.getParent());
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), plans);
    var before = ((JsonNode) plans.get("before")).path("Plan");
    var after = ((JsonNode) plans.get("after")).path("Plan");
    assertThat(after.path("Actual Rows").asInt()).isEqualTo(1);
    assertThat(before.path("Actual Rows").asInt()).isEqualTo(1);
    long oldBuffers =
        before.path("Shared Hit Blocks").asLong() + before.path("Shared Read Blocks").asLong();
    long newBuffers =
        after.path("Shared Hit Blocks").asLong() + after.path("Shared Read Blocks").asLong();
    assertThat(newBuffers).isLessThan(100);
    assertThat(newBuffers * 5).isLessThan(oldBuffers);
  }

  private JsonNode explain(String sql, Object... args) {
    try {
      return objectMapper
          .readTree(
              jdbc.queryForObject(
                  "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + sql, String.class, args))
          .get(0);
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private void seed(int count, boolean oneKey) {
    jdbc.update(
        "insert into auction_shipments (id, auction_house_id, shipment_date, status, created_at, updated_at) select ?+n, ?, date '2045-01-01' + case when ? then 0 else n end, 'SHIPPED', now(), now() from generate_series(1,?) n",
        BASE,
        house,
        oneKey,
        count);
    jdbc.update(
        "insert into auction_shipment_lots (id, shipment_id, variety_name, item_name, shipped_quantity, sold_quantity, waiting_quantity, returned_quantity, current_status, version, created_at, updated_at) select id,id,'품종','난',1,1,0,0,'SOLD',0,now(),now() from auction_shipments");
    jdbc.update(
        "insert into auction_attempts (id, shipment_lot_id, attempt_no, attempt_status, auction_date, created_at, updated_at) select id,id,1,'SOLD',shipment_date,now(),now() from auction_shipments");
    jdbc.update(
        "insert into auction_result_lines (id, auction_attempt_id, auction_date, quantity, unit_price, amount, inspection_status, created_at, updated_at) select id,id,auction_date,1,1000,1000,'NORMAL',now(),now() from auction_attempts");
  }

  private void append(long id, LocalDate date) {
    jdbc.update(
        "insert into auction_shipments (id, auction_house_id, shipment_date, status, created_at, updated_at) values (?, ?, ?, 'SHIPPED', now(), now())",
        id,
        house,
        date);
    jdbc.update(
        "insert into auction_shipment_lots (id, shipment_id, variety_name, item_name, shipped_quantity, sold_quantity, waiting_quantity, returned_quantity, current_status, version, created_at, updated_at) values (?,?,'품종','난',1,1,0,0,'SOLD',0,now(),now())",
        id,
        id);
    jdbc.update(
        "insert into auction_attempts (id, shipment_lot_id, attempt_no, attempt_status, auction_date, created_at, updated_at) values (?,?,1,'SOLD',?,now(),now())",
        id,
        id,
        date);
    jdbc.update(
        "insert into auction_result_lines (id, auction_attempt_id, auction_date, quantity, unit_price, amount, inspection_status, created_at, updated_at) values (?,?,?,1,1000,1000,'NORMAL',now(),now())",
        id,
        id,
        date);
  }
}
