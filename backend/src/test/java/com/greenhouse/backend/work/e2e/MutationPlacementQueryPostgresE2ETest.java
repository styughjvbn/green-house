package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.api.orchid.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.orchid.domain.OrchidGroup;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@Import(QueryShapeCapture.Configuration.class)
class MutationPlacementQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired OrchidGroupMutationEngine engine;
  @Autowired TransactionTemplate transactions;
  @Autowired EntityManagerFactory emf;
  @Autowired QueryShapeCapture capture;

  @ParameterizedTest
  @CsvSource({"1,1", "1,100", "1,1000", "10,1", "10,100", "10,1000", "50,1", "50,100", "50,1000"})
  void placementReadsAndFlushesDoNotGrowWithResultOrExistingGroupCount(int results, int existing)
      throws Exception {
    var fixture = seed(existing, results);
    var items = new ArrayList<CreateOrchidGroupMutationItem>();
    for (int i = 0; i < results; i++)
      items.add(
          new CreateOrchidGroupMutationItem(
              fixture.zone(), details(fixture.variety(), null, null)));
    var command =
        new CreateInboundOrchidGroupsMutationCommand(
            source("batch-" + results + "-" + existing),
            fixture.inbound(),
            items,
            LocalDate.of(2026, 8, 20),
            "배치 검사");
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    capture.start();
    long started = System.nanoTime();
    var response = transactions.execute(tx -> engine.createFromInbound(command));
    long elapsed = System.nanoTime() - started;
    var sql = capture.stop();
    long placementReads =
        sql.stream()
            .filter(
                q ->
                    q.startsWith("select")
                        && q.contains("from orchid_groups")
                        && q.contains("bed_zone_id")
                        && q.contains("quantity>?"))
            .count();
    // The new scalar query has a literal positive-quantity predicate.
    placementReads +=
        sql.stream()
            .filter(
                q ->
                    q.startsWith("select")
                        && q.contains("from orchid_groups")
                        && q.contains("bed_zone_id")
                        && q.contains("quantity>0"))
            .count();
    var report =
        Path.of("build/work-query-count/mutation-placement-" + results + "-" + existing + ".json");
    Files.createDirectories(report.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            report.toFile(),
            Map.of(
                "resultCount",
                results,
                "existingGroupCount",
                existing,
                "placementReads",
                placementReads,
                "groupLoads",
                stats.getEntityStatistics(OrchidGroup.class.getName()).getLoadCount(),
                "flushes",
                stats.getFlushCount(),
                "preparedStatements",
                stats.getPrepareStatementCount(),
                "transactionElapsedMs",
                elapsed / 1000000.0));
    assertThat(response.entries()).hasSize(results);
    for (int i = 0; i < results; i++) {
      var snapshot = response.entries().get(i).afterState();
      assertThat(snapshot.startPosition()).isEqualByComparingTo(BigDecimal.valueOf(i));
      assertThat(snapshot.endPosition()).isEqualByComparingTo(BigDecimal.valueOf(i + 1));
      assertThat(snapshot.sortOrder()).isEqualTo(existing + i + 1);
      assertThat(snapshot.inboundRecordId()).isEqualTo(fixture.inbound());
    }
    assertThat(placementReads).isEqualTo(1);
    assertThat(stats.getEntityStatistics(OrchidGroup.class.getName()).getLoadCount()).isZero();
    assertThat(stats.getFlushCount()).isLessThanOrEqualTo(3);
    assertThat(transactions.execute(tx -> engine.createFromInbound(command)).mutationId())
        .isEqualTo(response.mutationId());
  }

  @Test
  void competingInboundBatchesRefreshOccupiedRangesAfterAcquiringTheZoneLock() throws Exception {
    var fixture = seed(10, 100);
    long secondInbound =
        jdbc.queryForObject(
            "insert into inbound_records (created_at, updated_at, inbound_date, inbound_type, variety_id, status, estimated_quantity) values (now(), now(), date '2026-08-20', 'PRODUCT_POT', ?, 'PLACED', 50) returning id",
            Long.class,
            fixture.variety());
    var items = new ArrayList<CreateOrchidGroupMutationItem>();
    for (int i = 0; i < 50; i++)
      items.add(
          new CreateOrchidGroupMutationItem(
              fixture.zone(), details(fixture.variety(), null, null)));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(fixture.inbound(), secondInbound).stream()
              .map(
                  inbound ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(5, TimeUnit.SECONDS))
                              throw new IllegalStateException("start timeout");
                            return transactions.execute(
                                tx ->
                                    engine.createFromInbound(
                                        new CreateInboundOrchidGroupsMutationCommand(
                                            source("parallel-" + inbound),
                                            inbound,
                                            items,
                                            LocalDate.of(2026, 8, 20),
                                            null)));
                          }))
              .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      for (var future : futures) assertThat(future.get(15, TimeUnit.SECONDS).entries()).hasSize(50);
    } finally {
      start.countDown();
    }
    var starts =
        jdbc.queryForList(
            "select start_position from orchid_groups where inbound_record_id is not null order by start_position",
            BigDecimal.class);
    assertThat(starts).hasSize(100);
    for (int i = 0; i < 100; i++)
      assertThat(starts.get(i)).isEqualByComparingTo(BigDecimal.valueOf(i));
    assertThat(
            jdbc.queryForObject("select count(*) from orchid_group_mutation_entries", Long.class))
        .isEqualTo(100);
  }

  @Test
  void laterOverlapRollsBackEarlierResultsAndLedgerThenSameSourceCanRetry() {
    var fixture = seed(1, 3);
    var source = source("rollback-overlap");
    var first =
        new CreateOrchidGroupMutationItem(fixture.zone(), details(fixture.variety(), "0", "1"));
    var overlapping =
        new CreateOrchidGroupMutationItem(fixture.zone(), details(fixture.variety(), "0", "2"));
    assertThatThrownBy(
            () ->
                transactions.execute(
                    tx ->
                        engine.createFromInbound(
                            new CreateInboundOrchidGroupsMutationCommand(
                                source,
                                fixture.inbound(),
                                List.of(first, overlapping),
                                LocalDate.of(2026, 8, 20),
                                null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("겹칩니다");
    assertThat(jdbc.queryForObject("select count(*) from orchid_groups", Long.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from orchid_group_mutations", Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject("select count(*) from orchid_group_mutation_entries", Long.class))
        .isZero();
    var second =
        new CreateOrchidGroupMutationItem(fixture.zone(), details(fixture.variety(), "1", "2"));
    var retried =
        transactions.execute(
            tx ->
                engine.createFromInbound(
                    new CreateInboundOrchidGroupsMutationCommand(
                        source,
                        fixture.inbound(),
                        List.of(first, second),
                        LocalDate.of(2026, 8, 20),
                        null)));
    assertThat(retried.entries()).hasSize(2);
  }

  private Fixture seed(int existing, int results) {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedBenchmark(1, existing);
    long zone =
        jdbc.queryForObject(
            "select bed_zone_id from orchid_groups where id = ?",
            Long.class,
            scenario.firstOrchidGroupId());
    long variety = jdbc.queryForObject("select min(id) from varieties", Long.class);
    jdbc.update(
        "update physical_beds set position_unit_count = ?",
        BigDecimal.valueOf(existing * 2L + results + 10));
    jdbc.update(
        "update orchid_groups set start_position = sort_order * 2 + ?, end_position = sort_order * 2 + ?",
        results,
        results + 1);
    long inbound =
        jdbc.queryForObject(
            "insert into inbound_records (created_at, updated_at, inbound_date, inbound_type, variety_id, status, estimated_quantity) values (now(), now(), date '2026-08-20', 'PRODUCT_POT', ?, 'PLACED', ?) returning id",
            Long.class,
            variety,
            results);
    return new Fixture(zone, variety, inbound);
  }

  private OrchidGroupMutationDetails details(long variety, String start, String end) {
    return new OrchidGroupMutationDetails(
        variety,
        1,
        "2치",
        1,
        "정상",
        "POT",
        null,
        false,
        start == null ? null : new BigDecimal(start),
        end == null ? null : new BigDecimal(end),
        null);
  }

  private OrchidGroupMutationSource source(String id) {
    return new OrchidGroupMutationSource(
        OrchidGroupMutationSourceDomain.INBOUND,
        "INBOUND_RECORD",
        id,
        "CREATE_GROUPS",
        UUID.randomUUID());
  }

  private record Fixture(long zone, long variety, long inbound) {}
}
