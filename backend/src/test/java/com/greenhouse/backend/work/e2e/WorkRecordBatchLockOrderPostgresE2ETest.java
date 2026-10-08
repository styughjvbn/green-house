package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.MoveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateItem;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupBatchUpdateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.api.effect.StructureChangeCommand;
import com.greenhouse.backend.work.api.effect.StructureChangeResultInput;
import com.greenhouse.backend.work.api.effect.StructureChangeResultPurpose;
import com.greenhouse.backend.work.api.effect.StructureChangeSourceInput;
import com.greenhouse.backend.work.application.operation.StructureChangeRecordService;
import com.greenhouse.backend.work.application.operation.WorkOperationView;
import com.greenhouse.backend.work.application.target.WorkTargetSelection;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.spi.operation.StructureChangeRecordLockPort;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class WorkRecordBatchLockOrderPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private VarietyRepository varieties;
  @Autowired private StructureChangeRecordService records;
  @Autowired private OrchidGroupCommandService corrections;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private OrchidGroupMutationEngine engine;
  @MockitoSpyBean private StructureChangeRecordLockPort recordLocks;
  private Long low;
  private Long high;
  private Long otherLow;
  private Long otherHigh;
  private Long movementType;
  private Long repotType;
  private Map<Long, Long> zoneIds;
  private Map<Long, Long> varietyIds;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    low = scenario.orchidGroupId();
    Long firstVariety =
        jdbc.queryForObject("SELECT variety_id FROM orchid_groups WHERE id = ?", Long.class, low);
    Long secondVariety =
        varieties
            .saveAndFlush(
                new Variety("LOCK-W-B", "팔레놉시스", "잠금 품종 B", null, "3.5치", true, true, null, null))
            .getId();
    Long secondZone =
        jdbc.queryForObject(
            """
        SELECT min(z.id) FROM bed_zones z JOIN physical_beds b ON b.id = z.physical_bed_id
        WHERE b.house_id <> (SELECT b0.house_id FROM physical_beds b0
          JOIN bed_zones z0 ON z0.physical_bed_id = b0.id WHERE z0.id = ?)
        """,
            Long.class,
            scenario.bedZoneId());
    high = cloneGroup(secondZone, secondVariety, 1, 0);
    otherLow = cloneGroup(scenario.bedZoneId(), firstVariety, 2, 5);
    otherHigh = cloneGroup(secondZone, secondVariety, 2, 5);
    zoneIds =
        Map.of(
            low,
            scenario.bedZoneId(),
            high,
            secondZone,
            otherLow,
            scenario.bedZoneId(),
            otherHigh,
            secondZone);
    varietyIds =
        Map.of(
            low,
            firstVariety,
            high,
            secondVariety,
            otherLow,
            firstVariety,
            otherHigh,
            secondVariety);
    jdbc.update("UPDATE orchid_groups SET pot_size = ?", PotSizeCode.POT_3_5.getDisplayValue());
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    jdbc.execute("TRUNCATE audit_events CONTINUE IDENTITY");
    movementType =
        jdbc.queryForObject("SELECT id FROM work_types WHERE code = 'MOVEMENT'", Long.class);
    repotType = scenario.repotWorkTypeId();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void reverseSourceOrdersSerializeBeforePlanningAndKeepFreshSnapshots(boolean highFirst)
      throws Exception {
    var firstIds = highFirst ? List.of(high, low) : List.of(low, high);
    var secondIds = highFirst ? List.of(low, high) : List.of(high, low);
    var first = new AtomicReference<List<WorkOperationView>>();
    var second = new AtomicReference<List<WorkOperationView>>();
    var race =
        compete(
            () -> first.set(records.createStructureChangeRecords(batch(firstIds, "first", 10))),
            () -> second.set(records.createStructureChangeRecords(batch(secondIds, "second", 14))));
    assertSuccess(race);
    assertOrder(first.get(), firstIds);
    assertOrder(second.get(), secondIds);
    for (Long id : secondIds) {
      int index = firstIds.indexOf(id);
      var operation = second.get().get(secondIds.indexOf(id));
      assertThat(mutationStartBefore(operation.id(), id))
          .isEqualByComparingTo(BigDecimal.valueOf(10 + index * 2));
      assertGroup(id, 100, 2, 14 + secondIds.indexOf(id) * 2);
    }
    assertCompletedCounts(4, 2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void disjointSourceSetsWithReverseZoneOrdersCommitWithoutADeadlock(boolean highFirst)
      throws Exception {
    var firstIds = highFirst ? List.of(high, low) : List.of(low, high);
    var secondIds = highFirst ? List.of(otherLow, otherHigh) : List.of(otherHigh, otherLow);
    var race =
        compete(
            () -> records.createStructureChangeRecords(batch(firstIds, "zones-first", 10)),
            () -> records.createStructureChangeRecords(batch(secondIds, "zones-second", 14)));
    assertSuccess(race);
    for (int i = 0; i < 2; i++) {
      assertGroup(firstIds.get(i), 100, 1, 10 + i * 2);
      assertGroup(secondIds.get(i), 100, 1, 14 + i * 2);
    }
    assertCompletedCounts(4, 2);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void farmCorrectionAndWorkBatchUseTheSameRootThenZoneOrder(boolean farmWins) throws Exception {
    var workViews = new AtomicReference<List<WorkOperationView>>();
    Runnable work =
        () ->
            workViews.set(
                records.createStructureChangeRecords(batch(List.of(high, low), "farm-work", 10)));
    Runnable farm =
        () ->
            corrections.updateBatch(
                new OrchidGroupBatchUpdateRequest(
                    List.of(
                        new OrchidGroupBatchUpdateItem(low, correction(low)),
                        new OrchidGroupBatchUpdateItem(high, correction(high)))));
    var race = compete(farmWins ? farm : work, farmWins ? work : farm);
    assertSuccess(race);
    assertOrder(workViews.get(), List.of(high, low));
    assertGroup(low, 100, 2, farmWins ? 12 : 0);
    assertGroup(high, 100, 2, farmWins ? 10 : 0);
    if (farmWins) {
      assertThat(workViews.get())
          .allSatisfy(view -> assertThat(view.targets().getFirst().ageYearSnapshot()).isEqualTo(3));
    } else {
      assertThat(
              jdbc.queryForObject(
                  "SELECT (before_data ->> 'startPosition')::numeric FROM audit_events WHERE entity_id = ? AND source = 'ORCHID_GROUP_CORRECTION'",
                  BigDecimal.class,
                  high))
          .isEqualByComparingTo("10");
    }
    assertCompletedCounts(2, 1);
  }

  @Test
  void parallelSameKeyRequestReplaysTheBatchWithoutNewEffects() throws Exception {
    var request = batch(List.of(high, low), "same-key", 10);
    var first = new AtomicReference<List<WorkOperationView>>();
    var second = new AtomicReference<List<WorkOperationView>>();
    var race =
        compete(
            () -> first.set(records.createStructureChangeRecords(request)),
            () -> second.set(records.createStructureChangeRecords(request)));
    assertSuccess(race);
    assertThat(second.get())
        .extracting(WorkOperationView::id)
        .containsExactlyElementsOf(first.get().stream().map(WorkOperationView::id).toList());
    assertCompletedCounts(2, 1);
    assertGroup(high, 100, 1, 10);
    assertGroup(low, 100, 1, 12);
  }

  @Test
  void conflictingReplayLeavesEveryPersistedValueUnchanged() {
    var request = batch(List.of(high, low), "conflict", 10);
    records.createStructureChangeRecords(request);
    var before = snapshot();
    var altered =
        new StructureChangeRecordBatchCreateRequest(
            List.of(record(high, "conflict-0", movementType, 14, null), request.records().get(1)));
    assertThatThrownBy(() -> records.createStructureChangeRecords(altered))
        .isInstanceOf(ConflictException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void laterFailureRollsBackReceiptOperationsEffectsMutationsAndAuditThenAllowsSameKeys() {
    var good = batch(List.of(low, high), "retry", 10);
    var bad =
        new StructureChangeRecordBatchCreateRequest(
            List.of(good.records().get(0), record(high, "retry-1", movementType, 9, null)));
    var before = snapshot();
    assertThatThrownBy(() -> records.createStructureChangeRecords(bad))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
    records.createStructureChangeRecords(good);
    assertCompletedCounts(2, 1);
  }

  @Test
  void failedWinningBatchReleasesLocksAndWaitingBatchUsesOriginalSnapshots() throws Exception {
    var bad =
        new StructureChangeRecordBatchCreateRequest(
            List.of(
                record(low, "failed-0", movementType, 10, null),
                record(high, "failed-1", movementType, 9, null)));
    var response = new AtomicReference<List<WorkOperationView>>();
    var race =
        compete(
            () -> records.createStructureChangeRecords(bad),
            () ->
                response.set(
                    records.createStructureChangeRecords(
                        batch(List.of(high, low), "after-failure", 14))));
    assertThat(race.winnerFailure()).isInstanceOf(IllegalArgumentException.class);
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
    for (var operation : response.get())
      assertThat(
              mutationStartBefore(operation.id(), operation.targets().getFirst().orchidGroupId()))
          .isEqualByComparingTo("0");
    assertCompletedCounts(2, 1);
  }

  @Test
  void completedTransformBatchCanReplayAfterItsSourcesBecomeInactive() {
    var request =
        new StructureChangeRecordBatchCreateRequest(
            List.of(
                record(low, "repot-0", repotType, 10, null),
                record(high, "repot-1", repotType, 12, null)));
    var initial = records.createStructureChangeRecords(request);
    assertThat(
            jdbc.queryForList(
                "SELECT quantity FROM orchid_groups WHERE id IN (?, ?) ORDER BY id",
                Integer.class,
                low,
                high))
        .containsExactly(0, 0);
    var before = snapshot();
    doThrow(new AssertionError("receipt replay must not acquire fresh record locks"))
        .when((StructureChangeRecordLockPort) AopTestUtils.getUltimateTargetObject(recordLocks))
        .lock(anyCollection(), anyCollection());
    var replay = records.createStructureChangeRecords(request);
    assertThat(replay)
        .extracting(WorkOperationView::id)
        .containsExactlyElementsOf(initial.stream().map(WorkOperationView::id).toList());
    assertThat(snapshot()).isEqualTo(before);
    assertCompletedCounts(2, 1);
  }

  @Test
  void waitingSameKeyCanClaimAndApplyCorrectedInputAfterTheWinnerRollsBack() throws Exception {
    var good = batch(List.of(low, high), "same-key-rollback", 10);
    var bad =
        new StructureChangeRecordBatchCreateRequest(
            List.of(
                good.records().get(0), record(high, "same-key-rollback-1", movementType, 9, null)));
    var race =
        compete(
            () -> records.createStructureChangeRecords(bad),
            () -> records.createStructureChangeRecords(good));
    assertThat(race.winnerFailure()).isInstanceOf(IllegalArgumentException.class);
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
    assertGroup(low, 100, 1, 10);
    assertGroup(high, 100, 1, 12);
    assertCompletedCounts(2, 1);
  }

  @Test
  void changedQuantityAfterWaitingRejectsTheRecordAndRollsBackItsReceipt() throws Exception {
    var afterWinner = new AtomicReference<Map<String, List<String>>>();
    var original = correction(high);
    var corrected =
        new OrchidGroupUpdateRequest(
            original.varietyId(),
            90,
            original.potSize(),
            original.ageYear(),
            original.status(),
            original.placementType(),
            original.trayCount(),
            original.splitPlacementAllowed(),
            original.startPosition(),
            original.endPosition(),
            null);
    var race =
        compete(
            () -> {
              corrections.update(high, corrected);
              entityManager.flush();
              afterWinner.set(snapshot());
            },
            () ->
                records.createStructureChangeRecords(
                    batch(List.of(high, low), "changed-quantity", 10)));
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(afterWinner.get());
    assertGroup(high, 90, 1, 0);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @SuppressWarnings("deprecation")
  @Test
  void compatibilitySingleRecordAndBatchShareTheLockProtocol() throws Exception {
    var race =
        compete(
            () ->
                records.createStructureChangeRecord(
                    record(high, "compatibility", movementType, 10, null)),
            () ->
                records.createStructureChangeRecords(
                    batch(List.of(low, high), "compatibility-batch", 14)));
    assertSuccess(race);
    assertGroup(high, 100, 2, 16);
    assertGroup(low, 100, 1, 14);
    assertCompletedCounts(3, 2);
  }

  @Test
  void recordLockPortRequiresTheUseCaseTransaction() {
    var before = snapshot();
    assertThatThrownBy(() -> recordLocks.lock(List.of(low), List.of(zoneIds.get(low))))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void unexpectedPlannedTargetsFailWithoutAnyPartialRecord() {
    var original = record(low, "scope", movementType, 10, null);
    var request =
        new StructureChangeRecordBatchCreateRequest(
            List.of(
                new StructureChangeRecordCreateRequest(
                    new WorkOperationCreateRequest(
                        movementType,
                        "범위 불일치",
                        DATE,
                        null,
                        WorkTargetSelection.identifiedScope(WorkSourceScopeType.FARM, null),
                        null,
                        "시험",
                        null,
                        List.of()),
                    original.execution())));
    var before = snapshot();
    assertThatThrownBy(() -> records.createStructureChangeRecords(request))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void missingResultZoneRollsBackTheWholeRecordRequest() {
    var before = snapshot();
    var request =
        new StructureChangeRecordBatchCreateRequest(
            List.of(record(low, "missing-zone", movementType, 10, -1L)));
    assertThatThrownBy(() -> records.createStructureChangeRecords(request))
        .isInstanceOf(NotFoundException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void missingSourcePreservesTheValidationResponseAndRollsBackItsReceipt() throws Exception {
    var original = record(low, "missing-source", movementType, 10, null);
    var request =
        new StructureChangeRecordBatchCreateRequest(
            List.of(
                new StructureChangeRecordCreateRequest(
                    new WorkOperationCreateRequest(
                        movementType,
                        "없는 원본",
                        DATE,
                        null,
                        WorkTargetSelection.manualSelection(List.of(-99L)),
                        null,
                        "시험",
                        null,
                        List.of()),
                    new StructureChangeCommand(
                        "missing-source",
                        DATE,
                        "시험",
                        null,
                        List.of(new StructureChangeSourceInput(-99L, 100, null, null)),
                        original.execution().results()))));
    var before = snapshot();
    var response =
        post(
            "/api/work-operations/structure-change-records/batch",
            objectMapper.writeValueAsString(request));
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  private StructureChangeRecordBatchCreateRequest batch(List<Long> ids, String key, int start) {
    return new StructureChangeRecordBatchCreateRequest(
        IntStream.range(0, ids.size())
            .mapToObj(i -> record(ids.get(i), key + "-" + i, movementType, start + 2 * i, null))
            .toList());
  }

  private StructureChangeRecordCreateRequest record(
      Long id, String key, Long type, int start, Long zoneOverride) {
    return new StructureChangeRecordCreateRequest(
        new WorkOperationCreateRequest(
            type,
            "잠금 기록",
            DATE,
            null,
            WorkTargetSelection.manualSelection(List.of(id)),
            null,
            "시험",
            null,
            List.of()),
        new StructureChangeCommand(
            key,
            DATE,
            "시험",
            null,
            List.of(new StructureChangeSourceInput(id, 100, null, null)),
            List.of(
                new StructureChangeResultInput(
                    zoneOverride == null ? zoneIds.get(id) : zoneOverride,
                    100,
                    id,
                    "4치",
                    3,
                    StructureChangeResultPurpose.NORMAL,
                    "POT",
                    null,
                    false,
                    BigDecimal.valueOf(start),
                    BigDecimal.valueOf(start + 2),
                    null))));
  }

  private OrchidGroupUpdateRequest correction(Long id) {
    return new OrchidGroupUpdateRequest(
        varietyIds.get(id),
        100,
        "3.5치",
        3,
        "정상",
        "POT",
        null,
        false,
        BigDecimal.ZERO,
        BigDecimal.valueOf(5),
        null);
  }

  private Long cloneGroup(Long zone, Long variety, int sort, int start) {
    return jdbc.queryForObject(
        """
        INSERT INTO orchid_groups (created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
          split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        SELECT g.created_at, g.updated_at, g.age_year, v.genus, g.placement_type,
          g.pot_size, g.pot_size_code, 100, ?, g.status, v.name, ?,
          g.split_placement_allowed, v.id, ?, ?, 0 FROM orchid_groups g JOIN varieties v ON v.id = ? WHERE g.id = ? RETURNING id
        """,
        Long.class,
        sort,
        zone,
        start,
        start + 5,
        variety,
        low);
  }

  private RaceResult compete(Runnable winner, Runnable loser) throws Exception {
    var paused = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    var winnerMutations = new AtomicInteger();
    var loserMutations = new AtomicInteger();
    Answer<Object> answer =
        invocation -> {
          var result = invocation.callRealMethod();
          int pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
          if (pid == winnerPid.get() && winnerMutations.incrementAndGet() == 1) {
            entityManager.flush();
            paused.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
          } else if (pid == loserPid.get()) loserMutations.incrementAndGet();
          return result;
        };
    OrchidGroupMutationEngine target = AopTestUtils.getUltimateTargetObject(engine);
    doAnswer(answer).when(target).moveAll(any(MoveOrchidGroupsMutationCommand.class));
    doAnswer(answer).when(target).updateDetails(any(UpdateOrchidGroupMutationCommand.class));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> transaction(winnerPid, null, winner));
      try {
        assertThat(paused.await(10, TimeUnit.SECONDS)).isTrue();
        var second = executor.submit(() -> transaction(loserPid, started, loser));
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        boolean blocked = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
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
        assertThat(blocked).as("loser PID waits for winner transaction").isTrue();
        int mutations = loserMutations.get();
        resume.countDown();
        return new RaceResult(
            first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS), mutations);
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

  private void assertSuccess(RaceResult race) {
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserMutationsBeforeWaiting()).isZero();
  }

  private void assertOrder(List<WorkOperationView> operations, List<Long> ids) {
    assertThat(operations)
        .extracting(view -> view.targets().getFirst().orchidGroupId())
        .containsExactlyElementsOf(ids);
    assertThat(operations)
        .allSatisfy(view -> assertThat(view.status().name()).isEqualTo("COMPLETED"));
  }

  private void assertGroup(Long id, int quantity, long revision, int start) {
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, id))
        .isEqualTo(quantity);
    assertThat(
            jdbc.queryForObject(
                "SELECT state_revision FROM orchid_groups WHERE id = ?", Long.class, id))
        .isEqualTo(revision);
    assertThat(
            jdbc.queryForObject(
                "SELECT start_position FROM orchid_groups WHERE id = ?", BigDecimal.class, id))
        .isEqualByComparingTo(BigDecimal.valueOf(start));
  }

  private BigDecimal mutationStartBefore(Long operationId, Long groupId) {
    return jdbc.queryForObject(
        """
        SELECT (entry.before_state ->> 'startPosition')::numeric
        FROM orchid_group_mutation_entries entry
        JOIN work_applied_effects effect ON effect.mutation_id = entry.mutation_id
        WHERE effect.work_operation_id = ? AND entry.orchid_group_id = ?
        """,
        BigDecimal.class,
        operationId,
        groupId);
  }

  private void assertCompletedCounts(int operations, int receipts) {
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_operations WHERE status = 'COMPLETED'", Integer.class))
        .isEqualTo(operations);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_applied_effects WHERE mutation_id IS NOT NULL AND correlation_id IS NOT NULL",
                Integer.class))
        .isEqualTo(operations);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM work_command_receipts", Integer.class))
        .isEqualTo(receipts);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_command_receipt_memberships", Integer.class))
        .isEqualTo(operations);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Map<String, List<String>> snapshot() {
    var result = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "work_command_receipts",
            "work_command_receipt_memberships",
            "work_operations",
            "work_operation_targets",
            "work_target_executions",
            "work_applied_effects",
            "work_effect_orchid_groups",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "orchid_group_lineage",
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
