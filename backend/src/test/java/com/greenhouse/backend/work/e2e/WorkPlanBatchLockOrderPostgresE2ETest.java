package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.DiscardOrchidGroupMutationCommand;
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
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.api.target.WorkTargetSelection;
import com.greenhouse.backend.work.application.operation.DiscardRecordService;
import com.greenhouse.backend.work.application.operation.StructureChangeRecordService;
import com.greenhouse.backend.work.application.operation.WorkOperationAggregateCreator;
import com.greenhouse.backend.work.application.operation.WorkOperationPlanService;
import com.greenhouse.backend.work.dto.effect.DiscardRecordCreateRequest;
import com.greenhouse.backend.work.dto.effect.DiscardRecordResultRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordBatchCreateRequest;
import com.greenhouse.backend.work.dto.effect.StructureChangeRecordCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class WorkPlanBatchLockOrderPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private VarietyRepository varieties;
  @Autowired private WorkOperationPlanService plans;
  @Autowired private DiscardRecordService discards;
  @Autowired private StructureChangeRecordService records;
  @Autowired private OrchidGroupCommandService corrections;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private OrchidGroupLedgerReconciliationService reconciliation;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private WorkOperationAggregateCreator creator;
  @MockitoSpyBean private OrchidGroupMutationEngine engine;
  private Long low;
  private Long high;
  private Long leading;
  private Long zone;
  private Long repotType;
  private Long discardType;
  private Long movementType;
  private Map<Long, Long> varietyIds;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    low = scenario.orchidGroupId();
    zone = scenario.bedZoneId();
    repotType = scenario.repotWorkTypeId();
    discardType =
        jdbc.queryForObject("SELECT id FROM work_types WHERE code = 'DISCARD'", Long.class);
    movementType =
        jdbc.queryForObject("SELECT id FROM work_types WHERE code = 'MOVEMENT'", Long.class);
    Long firstVariety =
        jdbc.queryForObject("SELECT variety_id FROM orchid_groups WHERE id = ?", Long.class, low);
    Long secondVariety =
        varieties
            .saveAndFlush(
                new Variety(
                    "LOCK-P-B", "팔레놉시스", "계획 잠금 품종 B", null, "3.5치", true, true, null, null))
            .getId();
    jdbc.update(
        "UPDATE orchid_groups SET sort_order = 2, start_position = 5, end_position = 10 WHERE id = ?",
        low);
    high = cloneGroup(secondVariety, 1, 0);
    leading = cloneGroup(firstVariety, 0, 10);
    varietyIds = Map.of(low, firstVariety, high, secondVariety, leading, firstVariety);
    jdbc.update("UPDATE orchid_groups SET pot_size = ?", PotSizeCode.POT_3_5.getDisplayValue());
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    jdbc.execute("TRUNCATE audit_events CONTINUE IDENTITY");
    assertThat(low).isLessThan(high);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void planBatchAndFarmCorrectionDoNotDeadlockOrCommitStaleTargets(boolean farmWins)
      throws Exception {
    var views = new AtomicReference<List<WorkOperationView>>();
    var farmSnapshot = new AtomicReference<Map<String, List<String>>>();
    Runnable farm =
        () -> {
          correct();
          entityManager.flush();
          farmSnapshot.set(snapshot());
        };
    Runnable plan = () -> views.set(plans.createBatch(batch(List.of(leading))));
    var race = compete(farmWins ? farm : plan, farmWins ? plan : farm);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserStepsBeforeWaiting()).isZero();
    if (farmWins) {
      assertTargetChanged(race.loserFailure());
      assertWorkCounts(0, 0);
      assertThat(snapshot()).isEqualTo(farmSnapshot.get());
    } else {
      assertThat(race.loserFailure()).isNull();
      assertOrder(views.get(), List.of(high, low));
      assertThat(views.get())
          .allSatisfy(
              view -> assertThat(view.targets().getFirst().quantitySnapshot()).isEqualTo(100));
      assertWorkCounts(2, 0);
    }
    assertGroup(low, 80, 1);
    assertGroup(high, 80, 1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void discardRecordAndFarmCorrectionDoNotDeadlockOrApplyStaleEffects(boolean farmWins)
      throws Exception {
    var views = new AtomicReference<List<WorkOperationView>>();
    var farmSnapshot = new AtomicReference<Map<String, List<String>>>();
    Runnable farm =
        () -> {
          correct();
          entityManager.flush();
          farmSnapshot.set(snapshot());
        };
    Runnable discard = () -> views.set(discards.create(discard(10, 10)));
    var race = compete(farmWins ? farm : discard, farmWins ? discard : farm);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserStepsBeforeWaiting()).isZero();
    if (farmWins) {
      assertTargetChanged(race.loserFailure());
      assertWorkCounts(0, 0);
      assertThat(snapshot()).isEqualTo(farmSnapshot.get());
    } else {
      assertThat(race.loserFailure()).isNull();
      assertOrder(views.get(), List.of(high, low));
      assertThat(views.get())
          .allSatisfy(view -> assertThat(view.status().name()).isEqualTo("COMPLETED"));
      assertWorkCounts(2, 2);
      assertThat(
              jdbc.queryForList(
                  "SELECT (before_state ->> 'quantity')::int FROM orchid_group_mutation_entries e JOIN work_applied_effects f ON f.mutation_id = e.mutation_id ORDER BY e.orchid_group_id",
                  Integer.class))
          .containsExactly(100, 100);
    }
    assertGroup(low, 80, farmWins ? 1 : 2);
    assertGroup(high, 80, farmWins ? 1 : 2);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void overlappingPlansWithOppositeVarietyOrdersPreserveTheirSelection(boolean largeWins)
      throws Exception {
    var smallViews = new AtomicReference<List<WorkOperationView>>();
    var largeViews = new AtomicReference<List<WorkOperationView>>();
    Runnable small = () -> smallViews.set(plans.createBatch(batch(List.of(leading))));
    Runnable large = () -> largeViews.set(plans.createBatch(batch(List.of())));
    var race = compete(largeWins ? large : small, largeWins ? small : large);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserFailure()).isNull();
    assertThat(race.loserStepsBeforeWaiting()).isZero();
    assertOrder(smallViews.get(), List.of(high, low));
    assertThat(largeViews.get()).hasSize(2);
    assertThat(largeViews.get().getFirst().targets())
        .extracting(target -> target.orchidGroupId())
        .containsExactly(leading, low);
    assertThat(largeViews.get().getLast().targets())
        .extracting(target -> target.orchidGroupId())
        .containsExactly(high);
    assertWorkCounts(4, 0);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_targets", Integer.class))
        .isEqualTo(5);
    for (Long id : List.of(low, high, leading)) assertGroup(id, 100, 0);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void planBatchAndStructureRecordKeepTheExistingSnapshotConflictContract(boolean recordWins)
      throws Exception {
    var planned = new AtomicReference<List<WorkOperationView>>();
    var completed = new AtomicReference<List<WorkOperationView>>();
    Runnable plan = () -> planned.set(plans.createBatch(batch(List.of(leading))));
    Runnable record =
        () -> {
          completed.set(
              records.createStructureChangeRecords(
                  new StructureChangeRecordBatchCreateRequest(
                      List.of(movement(high, "cross-high", 15), movement(low, "cross-low", 17)))));
        };
    var race = compete(recordWins ? record : plan, recordWins ? plan : record);
    assertThat(race.winnerFailure()).isNull();
    assertThat(race.loserStepsBeforeWaiting()).isZero();
    if (recordWins) {
      assertTargetChanged(race.loserFailure());
      assertWorkCounts(2, 2);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operation_targets", Integer.class))
          .isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM work_target_executions", Integer.class))
          .isEqualTo(2);
    } else {
      assertThat(race.loserFailure()).isNull();
      assertOrder(planned.get(), List.of(high, low));
      assertWorkCounts(4, 2);
    }
    assertOrder(completed.get(), List.of(high, low));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_operations WHERE status = 'COMPLETED'", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM work_command_receipts", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_command_receipt_memberships", Integer.class))
        .isEqualTo(2);
    assertGroup(low, 100, 1);
    assertGroup(high, 100, 1);
    assertGroup(leading, 100, 0);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void laterDiscardFailureRollsBackEveryPlanEffectMutationAndAudit() {
    var before = snapshot();
    assertThatThrownBy(() -> discards.create(discard(101, 10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
    var completed = discards.create(discard(10, 10));
    assertOrder(completed, List.of(high, low));
    assertWorkCounts(2, 2);
    assertGroup(low, 90, 1);
    assertGroup(high, 90, 1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingOrDuplicateDiscardResultsRollBackTheAlreadyCreatedPlans(boolean duplicate) {
    var before = snapshot();
    var result = new DiscardRecordResultRequest(high, 10, "선별");
    var request =
        new DiscardRecordCreateRequest(
            operation(discardType, List.of(leading)),
            DATE,
            "잠금 시험",
            duplicate ? List.of(result, result) : List.of(result));
    assertThatThrownBy(() -> discards.create(request)).isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void excludedSourceRemainsUnlockedWhileTheBatchPlansItsIncludedTargets() {
    WorkOperationAggregateCreator target = AopTestUtils.getUltimateTargetObject(creator);
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              try (var executor = Executors.newSingleThreadExecutor()) {
                Long unlocked =
                    executor
                        .submit(
                            () ->
                                new TransactionTemplate(transactionManager)
                                    .execute(
                                        status ->
                                            jdbc.queryForObject(
                                                "SELECT id FROM orchid_groups WHERE id = ? FOR UPDATE NOWAIT",
                                                Long.class,
                                                leading)))
                        .get(10, TimeUnit.SECONDS);
                assertThat(unlocked).isEqualTo(leading);
              }
              return result;
            })
        .when(target)
        .createForOrchidGroups(any(), any(), any(), any());
    assertOrder(plans.createBatch(batch(List.of(leading))), List.of(high, low));
    assertGroup(leading, 100, 0);
    assertWorkCounts(2, 0);
  }

  private StructureChangeRecordCreateRequest movement(Long id, String key, int start) {
    return new StructureChangeRecordCreateRequest(
        new WorkOperationCreateRequest(
            movementType,
            "계획 교차 이동",
            DATE,
            null,
            WorkTargetSelection.manualSelection(List.of(id)),
            null,
            "잠금 시험",
            null,
            List.of()),
        new StructureChangeCommand(
            key,
            DATE,
            "잠금 시험",
            null,
            List.of(new StructureChangeSourceInput(id, 100, null, null)),
            List.of(
                new StructureChangeResultInput(
                    zone,
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

  private WorkOperationBatchCreateRequest batch(List<Long> excludedIds) {
    return new WorkOperationBatchCreateRequest(operation(repotType, excludedIds));
  }

  private WorkOperationCreateRequest operation(Long type, List<Long> excludedIds) {
    return new WorkOperationCreateRequest(
        type,
        "품종별 계획 잠금",
        DATE,
        DATE,
        WorkTargetSelection.identifiedScope(WorkSourceScopeType.BED_ZONE, zone),
        Map.of(),
        "잠금 시험",
        null,
        excludedIds);
  }

  private DiscardRecordCreateRequest discard(int lowQuantity, int highQuantity) {
    return new DiscardRecordCreateRequest(
        operation(discardType, List.of(leading)),
        DATE,
        "잠금 시험",
        List.of(
            new DiscardRecordResultRequest(low, lowQuantity, "선별"),
            new DiscardRecordResultRequest(high, highQuantity, "선별")));
  }

  private void correct() {
    corrections.updateBatch(
        new OrchidGroupBatchUpdateRequest(
            List.of(
                new OrchidGroupBatchUpdateItem(low, correction(low, 5)),
                new OrchidGroupBatchUpdateItem(high, correction(high, 0)))));
  }

  private OrchidGroupUpdateRequest correction(Long id, int start) {
    return new OrchidGroupUpdateRequest(
        varietyIds.get(id),
        80,
        "3.5치",
        3,
        "정상",
        "POT",
        null,
        false,
        BigDecimal.valueOf(start),
        BigDecimal.valueOf(start + 5),
        null);
  }

  private Long cloneGroup(Long variety, int sort, int start) {
    return jdbc.queryForObject(
        """
        INSERT INTO orchid_groups (created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
          split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        SELECT g.created_at, g.updated_at, g.age_year, v.genus, g.placement_type,
          g.pot_size, g.pot_size_code, 100, ?, g.status, v.name, g.bed_zone_id,
          g.split_placement_allowed, v.id, ?, ?, 0
        FROM orchid_groups g JOIN varieties v ON v.id = ? WHERE g.id = ? RETURNING id
        """,
        Long.class,
        sort,
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
    var winnerSteps = new AtomicInteger();
    var loserSteps = new AtomicInteger();
    Answer<Object> answer =
        invocation -> {
          var result = invocation.callRealMethod();
          int pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
          if (pid == winnerPid.get() && winnerSteps.incrementAndGet() == 1) {
            entityManager.flush();
            paused.countDown();
            assertThat(resume.await(10, TimeUnit.SECONDS)).isTrue();
          } else if (pid == loserPid.get()) loserSteps.incrementAndGet();
          return result;
        };
    WorkOperationAggregateCreator creatorTarget = AopTestUtils.getUltimateTargetObject(creator);
    doAnswer(answer).when(creatorTarget).createForOrchidGroups(any(), any(), any(), any());
    OrchidGroupMutationEngine engineTarget = AopTestUtils.getUltimateTargetObject(engine);
    doAnswer(answer).when(engineTarget).updateDetails(any(UpdateOrchidGroupMutationCommand.class));
    doAnswer(answer).when(engineTarget).discard(any(DiscardOrchidGroupMutationCommand.class));
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
        int steps = loserSteps.get();
        resume.countDown();
        return new RaceResult(
            first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS), steps);
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

  private void assertTargetChanged(Throwable failure) {
    assertThat(failure).isInstanceOf(ConflictException.class);
    assertThat(((ConflictException) failure).getCode()).isEqualTo("WORK_TARGET_CHANGED");
  }

  private void assertOrder(List<WorkOperationView> views, List<Long> ids) {
    assertThat(views).hasSize(ids.size());
    for (int i = 0; i < ids.size(); i++)
      assertThat(views.get(i).targets().getFirst().orchidGroupId()).isEqualTo(ids.get(i));
  }

  private void assertGroup(Long id, int quantity, long revision) {
    var row =
        jdbc.queryForMap("SELECT quantity, state_revision FROM orchid_groups WHERE id = ?", id);
    assertThat(((Number) row.get("quantity")).intValue()).isEqualTo(quantity);
    assertThat(((Number) row.get("state_revision")).longValue()).isEqualTo(revision);
  }

  private void assertWorkCounts(int operations, int effects) {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operations", Integer.class))
        .isEqualTo(operations);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_applied_effects WHERE mutation_id IS NOT NULL AND correlation_id IS NOT NULL",
                Integer.class))
        .isEqualTo(effects);
  }

  private record RaceResult(
      Throwable winnerFailure, Throwable loserFailure, int loserStepsBeforeWaiting) {}

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
            "audit_events")) {
      result.put(
          table,
          jdbc.queryForList(
              "SELECT to_jsonb(row)::text FROM " + table + " row ORDER BY 1", String.class));
    }
    return result;
  }
}
