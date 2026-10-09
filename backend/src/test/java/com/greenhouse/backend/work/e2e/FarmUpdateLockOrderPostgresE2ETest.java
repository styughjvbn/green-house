package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.orchid.mutation.MoveOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.api.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.mutation.verification.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.orchid.application.OrchidGroupCommandService;
import com.greenhouse.backend.farm.orchid.domain.PotSizeCode;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupBatchUpdateItem;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupResponse;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.farm.variety.repository.VarietyRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class FarmUpdateLockOrderPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private OrchidGroupCommandService commands;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private VarietyRepository varieties;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private OrchidGroupMutationEngine engine;
  private Long low;
  private Long high;
  private Long otherLow;
  private Long otherHigh;
  private Long variety;
  private Long secondZone;
  private final Map<Long, Long> requestedVarieties = new HashMap<>();

  @BeforeEach
  void seed() {
    requestedVarieties.clear();
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    low = scenario.orchidGroupId();
    variety =
        jdbc.queryForObject("SELECT variety_id FROM orchid_groups WHERE id = ?", Long.class, low);
    secondZone =
        jdbc.queryForObject(
            """
        SELECT min(z.id) FROM bed_zones z JOIN physical_beds b ON b.id = z.physical_bed_id
        WHERE b.house_id <> (SELECT b0.house_id FROM physical_beds b0
          JOIN bed_zones z0 ON z0.physical_bed_id = b0.id WHERE z0.id = ?)
        """,
            Long.class,
            scenario.bedZoneId());
    high = cloneGroup(secondZone, 1, 0);
    otherLow = cloneGroup(scenario.bedZoneId(), 2, 5);
    otherHigh = cloneGroup(secondZone, 2, 5);
    // Raw seed data uses a legacy pot label; match actual Entity normalization for no-op tests.
    jdbc.update("UPDATE orchid_groups SET pot_size = ?", PotSizeCode.POT_3_5.getDisplayValue());
    assertThat(high).isGreaterThan(low);
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, LocalDate.of(2045, 1, 1), "1.0.0");
    ledgerFixture.activate(key);
    jdbc.execute("TRUNCATE audit_events CONTINUE IDENTITY");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reverseInputOrdersSerializeBeforeAnyBatchMutation(boolean highFirst) throws Exception {
    var winnerIds = highFirst ? List.of(high, low) : List.of(low, high);
    var loserIds = highFirst ? List.of(low, high) : List.of(high, low);
    var winnerResponse = new AtomicReference<List<OrchidGroupResponse>>();
    var loserResponse = new AtomicReference<List<OrchidGroupResponse>>();
    var race =
        compete(
            () -> winnerResponse.set(commands.updateBatch(batch(winnerIds, 90))),
            () -> loserResponse.set(commands.updateBatch(batch(loserIds, 80))));
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
    assertThat(winnerResponse.get())
        .extracting(OrchidGroupResponse::id)
        .containsExactlyElementsOf(winnerIds);
    assertThat(loserResponse.get())
        .extracting(OrchidGroupResponse::id)
        .containsExactlyElementsOf(loserIds);
    for (Long id : winnerIds) {
      assertQuantity(id, 80, 2);
      assertAuditTransition(id, 100, 90, "BATCH");
      assertAuditTransition(id, 90, 80, "BATCH");
    }
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void disjointGroupsWithReverseZoneOrdersSerializeBeforeMutation(boolean highZoneFirst)
      throws Exception {
    var winnerIds = highZoneFirst ? List.of(high, low) : List.of(low, high);
    var loserIds = highZoneFirst ? List.of(otherLow, otherHigh) : List.of(otherHigh, otherLow);
    var race =
        compete(
            () -> commands.updateBatch(batch(winnerIds, 90)),
            () -> commands.updateBatch(batch(loserIds, 80)));
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
    for (Long id : winnerIds) assertQuantity(id, 90, 1);
    for (Long id : loserIds) assertQuantity(id, 80, 1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void apparentNoOpWaitsForCurrentStateBeforeDecidingWhetherToMutate() throws Exception {
    var response = new AtomicReference<OrchidGroupResponse>();
    var race =
        compete(
            () -> commands.update(low, update(low, 80)),
            () -> response.set(commands.update(low, update(low, 100))));
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(response.get().quantity()).isEqualTo(100);
    assertQuantity(low, 100, 2);
    assertAuditTransition(low, 100, 80, "SINGLE");
    assertAuditTransition(low, 80, 100, "SINGLE");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void singleAndBatchUpdatesUseTheSameGroupThenZoneOrder(boolean singleWins) throws Exception {
    Runnable single = () -> commands.update(high, update(high, 70));
    Runnable batch = () -> commands.updateBatch(batch(List.of(low, high), 90));
    var race = compete(singleWins ? single : batch, singleWins ? batch : single);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
    assertQuantity(low, 90, 1);
    assertQuantity(high, singleWins ? 90 : 70, 2);
    assertAuditTransition(high, 100, singleWins ? 70 : 90, singleWins ? "SINGLE" : "BATCH");
    assertAuditTransition(
        high, singleWins ? 70 : 90, singleWins ? 90 : 70, singleWins ? "BATCH" : "SINGLE");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void lastItemFailureRollsBackPriorMutationAndAuditThenAllowsRetry() {
    var before = snapshot();
    assertThatThrownBy(
            () ->
                commands.updateBatch(
                    new OrchidGroupBatchUpdateRequest(
                        List.of(
                            new OrchidGroupBatchUpdateItem(low, update(low, 90)),
                            new OrchidGroupBatchUpdateItem(high, update(high, -1))))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
    commands.updateBatch(batch(List.of(low, high), 90));
    assertQuantity(low, 90, 1);
    assertQuantity(high, 90, 1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void failedWinnerRollsBackAndWaitingBatchCommitsFromOriginalStates() throws Exception {
    var race =
        compete(
            () ->
                commands.updateBatch(
                    new OrchidGroupBatchUpdateRequest(
                        List.of(
                            new OrchidGroupBatchUpdateItem(low, update(low, 90)),
                            new OrchidGroupBatchUpdateItem(high, update(high, -1))))),
            () -> commands.updateBatch(batch(List.of(high, low), 80)));
    assertThat(race.winnerFailure()).isInstanceOf(IllegalArgumentException.class);
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
    assertQuantity(low, 80, 1);
    assertQuantity(high, 80, 1);
    assertAuditTransition(low, 100, 80, "BATCH");
    assertAuditTransition(high, 100, 80, "BATCH");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type = 'UPDATE_DETAILS'",
                Integer.class))
        .isEqualTo(2);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void waitingUpdateUsesTheZoneAndAuditStateCommittedByAMove() throws Exception {
    var response = new AtomicReference<OrchidGroupResponse>();
    var replacement =
        new OrchidGroupUpdateRequest(
            variety,
            80,
            "3.5치",
            2,
            "정상",
            "POT",
            null,
            false,
            BigDecimal.valueOf(15),
            BigDecimal.valueOf(20),
            null);
    var race =
        compete(
            () ->
                engine.move(
                    new MoveOrchidGroupMutationCommand(
                        OrchidGroupMutationSources.farmRequest(
                            "LOCK_ORDER_TEST", low.toString(), "MOVE"),
                        low,
                        secondZone,
                        BigDecimal.TEN,
                        BigDecimal.valueOf(15),
                        LocalDate.of(2045, 1, 1),
                        "경쟁 이동")),
            () -> response.set(commands.update(low, replacement)),
            false);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(response.get().bedZoneId()).isEqualTo(secondZone);
    assertThat(response.get().startPosition()).isEqualByComparingTo("15");
    assertQuantity(low, 80, 2);
    assertThat(
            jdbc.queryForObject(
                """
        SELECT count(*) FROM audit_events WHERE entity_id = ?
          AND before_data ->> 'zoneId' = ? AND after_data ->> 'zoneId' = ?
          AND (before_data ->> 'startPosition')::numeric = 10
          AND (after_data ->> 'startPosition')::numeric = 15
        """,
                Integer.class,
                low,
                secondZone.toString(),
                secondZone.toString()))
        .isEqualTo(1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void missingBatchTargetLeavesEveryGroupAndHistoryUnchanged() {
    var before = snapshot();
    assertThatThrownBy(
            () ->
                commands.updateBatch(
                    new OrchidGroupBatchUpdateRequest(
                        List.of(
                            new OrchidGroupBatchUpdateItem(low, update(low, 90)),
                            new OrchidGroupBatchUpdateItem(Long.MAX_VALUE, update(high, 90))))))
        .isInstanceOf(NotFoundException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void duplicateTargetsRetainInputOrderAndIntermediateAuditStates() {
    var response =
        commands.updateBatch(
            new OrchidGroupBatchUpdateRequest(
                List.of(
                    new OrchidGroupBatchUpdateItem(low, update(low, 90)),
                    new OrchidGroupBatchUpdateItem(low, update(low, 80)))));
    assertThat(response).extracting(OrchidGroupResponse::quantity).containsExactly(90, 80);
    assertQuantity(low, 80, 2);
    assertAuditTransition(low, 100, 90, "BATCH");
    assertAuditTransition(low, 90, 80, "BATCH");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void unchangedSingleAndBatchRequestsDoNotCreateMutationOrAudit() {
    var before = snapshot();
    commands.update(low, update(low, 100));
    commands.updateBatch(batch(List.of(high, low, low), 100));
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 4})
  void noOpBatchLoadsDistinctVarietiesAndResponseDetailsWithoutPerGroupQueries(int count) {
    var ids = List.of(low, high, otherLow, otherHigh).subList(0, count);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              for (Long id : ids) {
                Long varietyId =
                    varieties
                        .saveAndFlush(
                            new Variety(
                                "LOCK-Q-" + id,
                                "팔레놉시스",
                                "조회 품종 " + id,
                                null,
                                "3.5치",
                                true,
                                true,
                                null,
                                null))
                        .getId();
                requestedVarieties.put(id, varietyId);
                var input = update(id, 100);
                engine.updateDetails(
                    new UpdateOrchidGroupMutationCommand(
                        OrchidGroupMutationSources.farmRequest(
                            "QUERY_FIXTURE", id.toString(), "VARIETY"),
                        id,
                        new OrchidGroupMutationDetails(
                            varietyId,
                            100,
                            input.potSize(),
                            input.ageYear(),
                            input.status(),
                            input.placementType(),
                            input.trayCount(),
                            input.splitPlacementAllowed(),
                            input.startPosition(),
                            input.endPosition(),
                            null),
                        LocalDate.of(2045, 1, 1),
                        "조회 fixture"));
              }
            });
    var before = snapshot();
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    var response = commands.updateBatch(batch(ids.reversed(), 100));
    long queryCount = statistics.getPrepareStatementCount();
    assertThat(response)
        .extracting(OrchidGroupResponse::id)
        .containsExactlyElementsOf(ids.reversed());
    assertThat(response)
        .extracting(OrchidGroupResponse::varietyId)
        .containsExactlyElementsOf(ids.reversed().stream().map(requestedVarieties::get).toList());
    assertThat(queryCount).isEqualTo(3);
    assertThat(snapshot()).isEqualTo(before);
  }

  private RaceResult compete(Runnable winner, Runnable loser) throws Exception {
    return compete(winner, loser, true);
  }

  private RaceResult compete(Runnable winner, Runnable loser, boolean pauseAfterFirstMutation)
      throws Exception {
    var paused = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    var winnerMutations = new AtomicInteger();
    var loserMutations = new AtomicInteger();
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              int pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
              if (pid == winnerPid.get()
                  && pauseAfterFirstMutation
                  && winnerMutations.incrementAndGet() == 1) {
                entityManager.flush();
                paused.countDown();
                assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
              } else if (pid == loserPid.get()) loserMutations.incrementAndGet();
              return result;
            })
        .when((OrchidGroupMutationEngine) AopTestUtils.getUltimateTargetObject(engine))
        .updateDetails(any(UpdateOrchidGroupMutationCommand.class));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () ->
                  transaction(
                      winnerPid,
                      null,
                      () -> {
                        winner.run();
                        if (!pauseAfterFirstMutation) {
                          entityManager.flush();
                          paused.countDown();
                          try {
                            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
                          } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                          }
                        }
                      }));
      try {
        assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> transaction(loserPid, started, loser));
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        boolean blocked = false;
        while (!second.isDone() && System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT ? = ANY(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).as("loser backend waits for the winning transaction").isTrue();
        int mutationsBeforeWaiting = loserMutations.get();
        resume.countDown();
        return new RaceResult(
            first.get(20, TimeUnit.SECONDS),
            second.get(20, TimeUnit.SECONDS),
            mutationsBeforeWaiting);
      } finally {
        resume.countDown();
      }
    }
  }

  private Throwable transaction(AtomicInteger pid, CountDownLatch started, Runnable action) {
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                jdbc.execute("SET LOCAL lock_timeout = '15s'");
                if (started != null) started.countDown();
                action.run();
              });
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private OrchidGroupBatchUpdateRequest batch(List<Long> ids, int quantity) {
    return new OrchidGroupBatchUpdateRequest(
        ids.stream().map(id -> new OrchidGroupBatchUpdateItem(id, update(id, quantity))).toList());
  }

  private OrchidGroupUpdateRequest update(Long id, int quantity) {
    int start = id.equals(otherLow) || id.equals(otherHigh) ? 5 : 0;
    return new OrchidGroupUpdateRequest(
        requestedVarieties.getOrDefault(id, variety),
        quantity,
        "3.5치",
        2,
        "정상",
        "POT",
        null,
        false,
        BigDecimal.valueOf(start),
        BigDecimal.valueOf(start + 5),
        null);
  }

  private Long cloneGroup(Long zoneId, int sort, int start) {
    return jdbc.queryForObject(
        """
        INSERT INTO orchid_groups (created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
          split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        SELECT created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, 100, ?, status, variety_name, ?,
          split_placement_allowed, variety_id, ?, ?, 0 FROM orchid_groups WHERE id = ? RETURNING id
        """,
        Long.class,
        sort,
        zoneId,
        start,
        start + 5,
        low);
  }

  private void assertQuantity(Long id, int quantity, long revision) {
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, id))
        .isEqualTo(quantity);
    assertThat(
            jdbc.queryForObject(
                "SELECT state_revision FROM orchid_groups WHERE id = ?", Long.class, id))
        .isEqualTo(revision);
  }

  private void assertAuditTransition(Long id, int before, int after, String mode) {
    assertThat(
            jdbc.queryForObject(
                """
        SELECT count(*) FROM audit_events WHERE entity_id = ? AND source = 'ORCHID_GROUP_CORRECTION'
          AND before_data ->> 'quantity' = ? AND after_data ->> 'quantity' = ?
          AND context_data ->> 'correctionMode' = ?
        """,
                Integer.class,
                id,
                Integer.toString(before),
                Integer.toString(after),
                mode))
        .isEqualTo(1);
  }

  private Map<String, List<String>> snapshot() {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "audit_events"))
      result.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    return result;
  }

  private record RaceResult(
      Throwable winnerFailure, Throwable loserFailure, int loserMutationsBeforeWaiting) {}
}
