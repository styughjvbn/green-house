package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.support.MovementTestSupport;
import com.greenhouse.backend.support.MovementTestSupport.MoveTestRequest;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.application.operation.WorkOperationVoidService;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCancellationRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;

@Tag("work-e2e")
@org.springframework.context.annotation.Import(MovementTestSupport.class)
class WorkBatchCancellationPostgresE2ETest extends WorkE2ETestBase {

  @Autowired WorkTestDataSeeder seeder;

  @MockitoSpyBean WorkOperationVoidService cancellations;

  @Autowired DataSource dataSource;

  @Autowired PlatformTransactionManager transactionManager;

  @Autowired JdbcTemplate jdbc;

  @Autowired MovementTestSupport movement;

  @Autowired OrchidGroupRepository groups;

  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;

  @Autowired OrchidGroupLedgerReconciliationService reconciliation;

  private List<Long> workIds;

  private List<Long> sourceIds;

  private List<Long> occupants;

  private Long resultId;

  private Long discardId;

  private Long zone;

  private Long variety;

  private void prepare() throws Exception {
    prepare(false);
  }

  private void prepare(boolean outsideWork) throws Exception {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    zone = scenario.bedZoneId();
    variety =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id=?",
            Long.class,
            scenario.orchidGroupId());
    UUID key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, LocalDate.of(2026, 8, 20), "1.0.0");
    ledgerFixture.activate(key);
    Long second = createGroup(zone, 50, 6, 8);
    sourceIds = List.of(scenario.orchidGroupId(), second);
    Long outsideId =
        outsideWork
            ? plan(
                jdbc.queryForObject("SELECT id FROM work_types WHERE code='PESTICIDE'", Long.class),
                List.of(sourceIds.getFirst()))
            : null;
    Long destination =
        jdbc.queryForObject(
            "SELECT id FROM bed_zones WHERE id <> ? ORDER BY id LIMIT 1", Long.class, zone);
    movement.move(
        sourceIds.get(0),
        new MoveTestRequest(destination, BigDecimal.ZERO, new BigDecimal("5"), "worker", null));
    movement.move(
        sourceIds.get(1),
        new MoveTestRequest(destination, new BigDecimal("6"), new BigDecimal("8"), "worker", null));
    Long transformId = plan(scenario.repotWorkTypeId(), sourceIds);
    assertThat(post("/api/work-operations/" + transformId + "/start", "{}").status())
        .isEqualTo(200);
    var execution =
        post(
            "/api/work-operations/" + transformId + "/structure-change-executions",
            """
						{"idempotencyKey":"transform","completedDate":"2026-07-15",
						 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":100},{"sourceOrchidGroupId":%d,"inputQuantity":50}],
						 "results":[{"bedZoneId":%d,"quantity":150,"potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":10,"endPosition":13}]}
						"""
                .formatted(sourceIds.get(0), sourceIds.get(1), zone));
    assertThat(execution.status()).as(execution.body().toString()).isEqualTo(201);
    resultId =
        jdbc.queryForObject(
            "SELECT link.orchid_group_id FROM work_effect_orchid_groups link JOIN work_applied_effects effect ON effect.id=link.work_applied_effect_id WHERE effect.work_operation_id=? AND link.relation_type='RESULT'",
            Long.class,
            transformId);
    Long discardType =
        jdbc.queryForObject("SELECT id FROM work_types WHERE code='DISCARD'", Long.class);
    discardId = plan(discardType, List.of(resultId));
    assertThat(post("/api/work-operations/" + discardId + "/start", "{}").status()).isEqualTo(200);
    Long target =
        jdbc.queryForObject(
            "SELECT id FROM work_operation_targets WHERE work_operation_id=?",
            Long.class,
            discardId);
    var discarded =
        post(
            "/api/work-operations/%d/targets/%d/complete".formatted(discardId, target),
            """
				{"completedDate":"2026-07-15","resultDetails":{"discardQuantity":150,"reason":"오등록"}}
				""");
    assertThat(discarded.status()).as(discarded.body().toString()).isEqualTo(200);
    workIds =
        jdbc.queryForList("SELECT id FROM work_operations ORDER BY id", Long.class).stream()
            .filter(id -> !id.equals(outsideId))
            .toList();
    occupants =
        List.of(
            createGroup(zone, 10, 0, 5),
            createGroup(zone, 10, 6, 8),
            createGroup(zone, 10, 10, 13));
  }

  @Test
  void cancelsTheWholeChainAndOriginalsWithoutRestoringOccupiedPositions() throws Exception {
    prepare();
    var single =
        post(
            "/api/work-operations/" + discardId + "/cancel",
            """
				{"idempotencyKey":"single","reason":"오등록"}
				""");
    assertThat(single.status()).isGreaterThanOrEqualTo(400);
    long links = jdbc.queryForObject("SELECT count(*) FROM work_effect_orchid_groups", Long.class);
    var response = post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "batch"));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
    Long compensation = response.data().path("compensationMutationId").asLong();
    assertThat(jdbc.queryForList("SELECT status FROM work_operations", String.class))
        .containsOnly("VOIDED");
    for (Long id : List.of(sourceIds.get(0), sourceIds.get(1), resultId)) {
      assertThat(groups.findById(id))
          .get()
          .satisfies(
              group -> {
                assertThat(group.getQuantity()).isZero();
                assertThat(group.getStatus()).isEqualTo("생성 취소");
              });
    }
    for (Long id : occupants)
      assertThat(groups.findById(id).orElseThrow().getQuantity()).isEqualTo(10);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM work_effect_orchid_groups", Long.class))
        .isEqualTo(links);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutation_relations WHERE mutation_id=? AND relation_type='COMPENSATES'",
                Long.class,
                compensation))
        .isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE context_data->>'compensationMutationId'=?",
                Long.class,
                compensation.toString()))
        .isEqualTo(3);
    assertThat(reconciliation.reconcile().ready()).isTrue();
    var replay =
        post(
            "/api/work-operations/cancel-batch",
            request(workIds.reversed(), sourceIds.reversed(), "batch"));
    assertThat(replay.status()).as(replay.body().toString()).isEqualTo(200);
    assertThat(replay.data().path("compensationMutationId").asLong()).isEqualTo(compensation);
    assertThat(
            post(
                    "/api/work-operations/cancel-batch",
                    request(workIds, List.of(sourceIds.get(0)), "batch"))
                .status())
        .isEqualTo(409);
  }

  @Test
  void concurrentRetriesCreateOnlyOneCompensationAndAuditSet() throws Exception {
    prepare();
    String payload = request(workIds, sourceIds, "parallel");
    var firstWorker = new PostgresLockTestSupport.Worker();
    var secondWorker = new PostgresLockTestSupport.Worker();
    var invocationOrder = new AtomicInteger();
    doAnswer(
            invocation -> {
              (invocationOrder.getAndIncrement() == 0 ? firstWorker : secondWorker).capture(jdbc);
              return invocation.callRealMethod();
            })
        .when(cancellations)
        .cancelBatch(any());
    try (var connection = dataSource.getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      int owner = PostgresLockTestSupport.backendPid(connection);
      try (var statement =
          connection.prepareStatement("SELECT id FROM work_operations WHERE id = ? FOR UPDATE")) {
        statement.setLong(1, workIds.stream().min(Long::compareTo).orElseThrow());
        try (var rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
        }
      }
      try {
        var first = executor.submit(() -> post("/api/work-operations/cancel-batch", payload));
        firstWorker.awaitBlockedBy(jdbc, owner, first);
        var second = executor.submit(() -> post("/api/work-operations/cancel-batch", payload));
        secondWorker.awaitBlockedBy(jdbc, owner, second);
        connection.commit();
        var a = first.get(30, TimeUnit.SECONDS);
        var b = second.get(30, TimeUnit.SECONDS);
        assertThat(a.status()).as(a.body().toString()).isEqualTo(200);
        assertThat(b.status()).as(b.body().toString()).isEqualTo(200);
        assertThat(a.data().path("compensationMutationId"))
            .isEqualTo(b.data().path("compensationMutationId"));
      } finally {
        connection.rollback();
      }
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type='COMPENSATION'",
                Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE context_data ? 'compensationMutationId'",
                Long.class))
        .isEqualTo(3);
  }

  @Test
  void missingDownstreamWorkAndUnrelatedCancellationIdsCannotBypassValidation() throws Exception {
    prepare();
    var missing =
        post(
            "/api/work-operations/cancel-batch",
            request(
                workIds.stream().filter(id -> !id.equals(discardId)).toList(),
                sourceIds,
                "missing"));
    assertThat(missing.status()).isGreaterThanOrEqualTo(400);
    var unrelated =
        post("/api/work-operations/cancel-batch", request(workIds, occupants, "unrelated"));
    assertThat(unrelated.status()).isGreaterThanOrEqualTo(400);
    assertUnchanged();
  }

  @Test
  void ordinaryRestorationStillChecksOccupiedOriginalPositions() throws Exception {
    prepare();
    var response =
        post("/api/work-operations/cancel-batch", request(workIds, List.of(), "restore"));
    assertThat(response.status()).isGreaterThanOrEqualTo(400);
    assertUnchanged();
  }

  @Test
  void unselectedRecordOnlyWorkStillBlocksTheBatch() throws Exception {
    prepare(true);
    var response =
        post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "external"));
    assertThat(response.status()).isGreaterThanOrEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_operations WHERE status='VOIDED'", Long.class))
        .isZero();
  }

  @Test
  void failureAfterMutationFlushRollsBackGroupsWorkAndAudit() throws Exception {
    prepare();
    var before = cancellationState();
    jdbc.execute(
        "ALTER TABLE work_operations ADD CONSTRAINT test_batch_failure CHECK (status <> 'VOIDED')");
    try {
      PostgresWriteTestSupport.assertStandaloneCheckFailure(
          () ->
              cancellations.cancelBatch(
                  new WorkOperationBatchCancellationRequest(
                      workIds, Set.copyOf(sourceIds), "rollback", "오등록")),
          "test_batch_failure");
      assertThat(cancellationState()).isEqualTo(before);
      var response =
          post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "rollback"));
      assertThat(response.status()).as(response.body().toString()).isEqualTo(409);
      assertThat(response.body().path("error").path("code").asText())
          .isEqualTo("DATA_INTEGRITY_CONFLICT");
      assertThat(cancellationState()).isEqualTo(before);
      assertUnchanged();
    } finally {
      jdbc.execute("ALTER TABLE work_operations DROP CONSTRAINT test_batch_failure");
    }
    var retry = post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "rollback"));
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(200);
    assertThat(reconciliation.reconcile().ready()).isTrue();
    var committed = cancellationState();
    assertThat(
            post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "rollback"))
                .data())
        .isEqualTo(retry.data());
    assertThat(cancellationState()).isEqualTo(committed);
  }

  private Map<String, List<String>> cancellationState() {
    return PostgresWriteTestSupport.snapshot(
        jdbc,
        transactionManager,
        List.of(
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "work_operations",
            "work_operation_targets",
            "work_target_executions",
            "work_applied_effects",
            "work_effect_orchid_groups",
            "work_command_receipts",
            "work_command_receipt_memberships",
            "audit_events"));
  }

  private void assertUnchanged() {
    assertThat(jdbc.queryForList("SELECT status FROM work_operations", String.class))
        .containsOnly("COMPLETED");
    assertThat(groups.findById(resultId).orElseThrow().getStatus()).isEqualTo("폐기");
    for (Long id : sourceIds)
      assertThat(groups.findById(id).orElseThrow().getStatus()).isEqualTo("종료");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type='COMPENSATION'",
                Long.class))
        .isZero();
  }

  @Test
  void batchesAcrossAnAlreadyCompensatedIntermediateWorkAndReplays() throws Exception {
    for (boolean finalCancel : List.of(false, true)) {
      prepareIndependentMoves(false, true);
      Long id = sourceIds.getFirst();
      Long first = workIds.getFirst();
      Long intermediate = moveForHistory(id, zone, 0, 5);
      assertThat(
              post(
                      "/api/work-operations/" + intermediate + "/cancel",
                      """
					{"idempotencyKey":"intermediate","reason":"오등록"}
					""")
                  .status())
          .isEqualTo(200);
      Long last = moveForHistory(id, zone, 12, 17);
      String payload =
          request(List.of(first, last), finalCancel ? List.of(id) : List.of(), "neutralized-gap");
      var response = post("/api/work-operations/cancel-batch", payload);
      assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
      assertThat(groups.findById(id).orElseThrow().getQuantity()).isEqualTo(finalCancel ? 0 : 100);
      assertThat(groups.findById(id).orElseThrow().getStatus())
          .isEqualTo(finalCancel ? "생성 취소" : "정상");
      assertThat(post("/api/work-operations/cancel-batch", payload).data())
          .isEqualTo(response.data());
      assertThat(reconciliation.reconcile().ready()).isTrue();
    }
  }

  @Test
  void doesNotSkipUncompensatedRoundTripsWithMatchingSnapshots() throws Exception {
    prepareIndependentMoves(false, true);
    Long id = sourceIds.getFirst();
    Long first = workIds.getFirst();
    Long destination = groups.findById(id).orElseThrow().getBedZone().getId();
    moveForHistory(id, zone, 0, 5);
    moveForHistory(id, destination, 0, 5);
    Long last = moveForHistory(id, zone, 12, 17);
    var response =
        post(
            "/api/work-operations/cancel-batch",
            request(List.of(first, last), List.of(id), "live-gap"));
    assertThat(response.status()).isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_operations WHERE status='VOIDED'", Long.class))
        .isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void eligibilityReportsTheSameOccupiedPlacementBlockerAsCancellation() throws Exception {
    prepareIndependentMoves(false, true);
    createGroup(zone, 10, 0, 5);
    var eligibility = get("/api/work-operations/" + workIds.getFirst() + "/cancel-eligibility");
    assertThat(eligibility.status()).as(eligibility.body().toString()).isEqualTo(200);
    assertThat(eligibility.data().path("cancellable").asBoolean()).isFalse();
    assertThat(eligibility.data().path("blockers").toString())
        .contains("RESTORATION_PLACEMENT_CONFLICT");
    assertThat(
            post(
                    "/api/work-operations/" + workIds.getFirst() + "/cancel",
                    """
				{"idempotencyKey":"occupied","reason":"오등록"}
				""")
                .status())
        .isEqualTo(400);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Long moveForHistory(Long id, Long destination, int start, int end) {
    movement.move(
        id,
        new MoveTestRequest(
            destination, BigDecimal.valueOf(start), BigDecimal.valueOf(end), "worker", null));
    return jdbc.queryForObject("SELECT max(id) FROM work_operations", Long.class);
  }

  @Test
  void rejectsOverlappingPlacementsBetweenRestoredGroupsWithoutAnyPartialChanges()
      throws Exception {
    assertRestorationConflict(true, true);
  }

  @Test
  void rejectsDuplicateSortOrdersBetweenNonOverlappingRestoredGroups() throws Exception {
    assertRestorationConflict(false, true);
  }

  @Test
  void rejectsDuplicateSortOrderWithAnUnselectedActiveGroup() throws Exception {
    assertRestorationConflict(false, false);
  }

  @Test
  void allowsCreationCancellationInsteadOfRestoringMutuallyConflictingPositions() throws Exception {
    prepareIndependentMoves(true, true);
    var response =
        post(
            "/api/work-operations/cancel-batch", request(workIds, sourceIds, "cancel-conflicting"));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
    assertThat(jdbc.queryForList("SELECT status FROM work_operations", String.class))
        .containsOnly("VOIDED");
    for (Long id : sourceIds) {
      assertThat(groups.findById(id).orElseThrow().getStatus()).isEqualTo("생성 취소");
    }
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private void assertRestorationConflict(boolean overlap, boolean moveSecond) throws Exception {
    prepareIndependentMoves(overlap, moveSecond);
    var beforeGroups = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    var beforeWorks = jdbc.queryForList("SELECT * FROM work_operations ORDER BY id");
    var response =
        post(
            "/api/work-operations/cancel-batch",
            request(workIds, List.of(), "restore-conflicting"));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id"))
        .isEqualTo(beforeGroups);
    assertThat(jdbc.queryForList("SELECT * FROM work_operations ORDER BY id"))
        .isEqualTo(beforeWorks);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type='COMPENSATION'",
                Long.class))
        .isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private void prepareIndependentMoves(boolean overlap, boolean moveSecond) throws Exception {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    zone = scenario.bedZoneId();
    variety =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id=?",
            Long.class,
            scenario.orchidGroupId());
    UUID key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, LocalDate.of(2026, 8, 20), "1.0.0");
    ledgerFixture.activate(key);
    Long destination =
        jdbc.queryForObject(
            "SELECT id FROM bed_zones WHERE id <> ? ORDER BY id LIMIT 1", Long.class, zone);
    movement.move(
        scenario.orchidGroupId(),
        new MoveTestRequest(destination, BigDecimal.ZERO, new BigDecimal("5"), "worker", null));
    Long second = createGroup(zone, 10, overlap ? 0 : 6, overlap ? 5 : 11);
    if (moveSecond) {
      movement.move(
          second,
          new MoveTestRequest(
              destination, new BigDecimal("6"), new BigDecimal("11"), "worker", null));
    }
    sourceIds = List.of(scenario.orchidGroupId(), second);
    workIds = jdbc.queryForList("SELECT id FROM work_operations ORDER BY id", Long.class);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private String request(List<Long> ids, List<Long> finalIds, String key) throws Exception {
    return """
				{"workOperationIds":%s,"creationCancellationOrchidGroupIds":%s,"idempotencyKey":"%s","reason":"오등록 일괄 취소"}
				"""
        .formatted(
            objectMapper.writeValueAsString(ids), objectMapper.writeValueAsString(finalIds), key);
  }

  private Long plan(Long type, List<Long> ids) throws Exception {
    var response =
        post(
            "/api/work-operations",
            """
						{"workTypeId":%d,"title":"취소 검증","plannedStartDate":"2026-07-15","sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":%s}
						"""
                .formatted(type, objectMapper.writeValueAsString(ids)));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
    return response.data().path("id").asLong();
  }

  private Long createGroup(Long targetZone, int quantity, int start, int end) throws Exception {
    var response =
        post(
            "/api/orchid-groups",
            """
						{"bedZoneId":%d,"varietyId":%d,"quantity":%d,"potSize":"4치","ageYear":2,"status":"정상","startPosition":%d,"endPosition":%d}
						"""
                .formatted(targetZone, variety, quantity, start, end));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
    return response.data().path("id").asLong();
  }
}
