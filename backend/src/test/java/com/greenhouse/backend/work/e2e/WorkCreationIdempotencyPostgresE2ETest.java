package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.application.operation.WorkOperationPlanService;
import com.greenhouse.backend.work.application.operation.WorkOperationProgressService;
import com.greenhouse.backend.work.application.operation.WorkOperationRelationQueryService;
import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import com.greenhouse.backend.work.dto.operation.WorkOperationBatchCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationCreateRequest;
import com.greenhouse.backend.work.dto.operation.WorkOperationRelationKind;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class WorkCreationIdempotencyPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private WorkOperationPlanService plans;
  @Autowired private WorkOperationProgressService progress;
  @Autowired private WorkOperationRelationQueryService relations;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  private Long otherGroupId;
  private Long repotTypeId;
  private Long groupId;
  private Long workTypeId;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    groupId = scenario.orchidGroupId();
    workTypeId = scenario.pesticideWorkTypeId();
    repotTypeId = scenario.repotWorkTypeId();
    jdbc.update("update work_types set is_active = true where id = ?", workTypeId);
    Long variety =
        jdbc.queryForObject(
            "insert into varieties (code, genus, name, sale_enabled, is_active, created_at, updated_at) values ('E2E-WORK-B', '팔레놉시스', '작업 품종 B', true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) returning id",
            Long.class);
    otherGroupId =
        jdbc.queryForObject(
            """
        insert into orchid_groups (created_at, updated_at, age_year, genus, placement_type,
          pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
          split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
        select g.created_at, g.updated_at, g.age_year, v.genus, g.placement_type,
          g.pot_size, g.pot_size_code, 100, 2, g.status, v.name, g.bed_zone_id,
          g.split_placement_allowed, v.id, 6, 11, 0
        from orchid_groups g join varieties v on v.id = ? where g.id = ? returning id
        """,
            Long.class,
            variety,
            groupId);
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
  }

  @Test
  void repeatedCreateReturnsFirstResponseWithoutDuplicatePlan() throws Exception {
    var body = objectMapper.writeValueAsString(request("계획"));
    var first = post("/api/work-operations", body, Map.of("Idempotency-Key", "retry"));
    var second = post("/api/work-operations", body, Map.of("Idempotency-Key", "retry"));
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(second.status()).as(second.body().toString()).isEqualTo(201);
    assertThat(second.data()).isEqualTo(first.data());
    assertThat(jdbc.queryForObject("select count(*) from work_operations", Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void replaysLegacyCreationHashAfterCurrentWorkChanges(Mode mode) throws Exception {
    var request = request("original title");
    var first = create(mode, request, "legacy-v1");
    Object legacyPayload =
        mode == Mode.BATCH ? new WorkOperationBatchCreateRequest(request) : request;
    var legacyHash = new WorkRequestFingerprint().calculate(legacyPayload);
    var scope =
        switch (mode) {
          case SINGLE -> "GENERAL_PLAN";
          case BATCH -> "GENERAL_PLAN_BATCH";
          case RECORD -> "GENERAL_RECORD";
        };
    var key = scope + ":legacy-v1";
    assertThat(
            jdbc.queryForObject(
                "select request_fingerprint from work_command_receipts where receipt_key = ?",
                String.class,
                key))
        .isEqualTo(legacyHash);
    jdbc.update(
        "update work_command_receipts set request_fingerprint = ? where receipt_key = ?",
        legacyHash,
        key);
    progress.updateTitle(first.getFirst().id(), "later title");
    jdbc.update("update work_types set is_active = false where id = ?", workTypeId);
    var before = snapshot();
    try {
      assertThat(responseJson(create(mode, request, "legacy-v1"))).isEqualTo(responseJson(first));
      assertThatThrownBy(() -> create(mode, request("changed title"), "legacy-v1"))
          .isInstanceOf(ConflictException.class)
          .satisfies(
              error ->
                  assertThat(((ConflictException) error).getCode())
                      .isEqualTo("IDEMPOTENCY_KEY_REUSED"));
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.update("update work_types set is_active = true where id = ?", workTypeId);
    }
  }

  @Test
  void changedInputWithSuccessfulKeyIsRejected() throws Exception {
    var first =
        post(
            "/api/work-operations",
            objectMapper.writeValueAsString(request("계획")),
            Map.of("Idempotency-Key", "retry"));
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    var changed =
        post(
            "/api/work-operations",
            objectMapper.writeValueAsString(request("변경")),
            Map.of("Idempotency-Key", "retry"));
    assertThat(changed.status()).as(changed.body().toString()).isEqualTo(409);
    assertThat(changed.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void replayPreservesAllTargetsEffectsAndMemberships(Mode mode) throws Exception {
    var request = request("입력");
    var first = http(mode, request, "each");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    var before = snapshot();
    var retry = http(mode, request, "each");
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertCounts(mode, 1);
    assertThat(count("work_command_receipts")).isEqualTo(1);
    assertThat(count("work_command_receipt_memberships")).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void replayIgnoresLaterTitleStatusAndInactiveWorkType(Mode mode) throws Exception {
    var request = request("최초");
    var first = http(mode, request, "later");
    assertThat(first.status()).isEqualTo(201);
    long id = (mode == Mode.BATCH ? first.data().get(0) : first.data()).path("id").asLong();
    progress.updateTitle(id, "변경된 제목");
    if (mode != Mode.RECORD) progress.start(id);
    jdbc.update("update work_types set is_active = false where id = ?", workTypeId);
    var before = snapshot();
    try {
      var retry = http(mode, request, "later");
      assertThat(retry.status()).isEqualTo(201);
      assertThat(retry.data()).isEqualTo(first.data());
      assertThat(snapshot()).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select title from work_operations where id = ?", String.class, id))
          .isEqualTo("변경된 제목");
    } finally {
      jdbc.update("update work_types set is_active = true where id = ?", workTypeId);
    }
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void replayAfterCancellationDoesNotReopenWork(Mode mode) throws Exception {
    var request = request("취소 전");
    var first = http(mode, request, "cancelled");
    assertThat(first.status()).isEqualTo(201);
    long id = (mode == Mode.BATCH ? first.data().get(0) : first.data()).path("id").asLong();
    var cancelled =
        post(
            "/api/work-operations/" + id + "/cancel",
            "{\"idempotencyKey\":\"cancel\",\"reason\":\"오등록\"}");
    assertThat(cancelled.status()).as(cancelled.body().toString()).isEqualTo(200);
    var before = snapshot();
    var retry = http(mode, request, "cancelled");
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, id))
        .isIn("CANCELED", "VOIDED");
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void receiptCompletionFailureRollsBackWorkEffectsAndAudit(Mode mode) {
    var before = snapshot();
    jdbc.execute(
        "alter table work_command_receipts add constraint test_work_receipt_failure check (response_snapshot is null)");
    try {
      assertThatThrownBy(() -> create(mode, request("실패"), "late"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_work_receipt_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table work_command_receipts drop constraint test_work_receipt_failure");
    }
    assertThat(create(mode, request("실패"), "late")).hasSize(1);
    assertCounts(mode, 1);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void membershipFailureAlsoRollsBackCompletedReceiptAndBusiness(Mode mode) {
    var before = snapshot();
    jdbc.execute(
        "alter table work_command_receipt_memberships add constraint test_work_membership_failure check (operation_id < 0)");
    try {
      assertThatThrownBy(() -> create(mode, request("실패"), "membership"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_work_membership_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute(
          "alter table work_command_receipt_memberships drop constraint test_work_membership_failure");
    }
    assertThat(create(mode, request("실패"), "membership")).hasSize(1);
    assertCounts(mode, 1);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void replayLoadsOnlyReceiptWithoutCurrentTargetOrWorkQueries(Mode mode) {
    var request = request("조회");
    var first = responseJson(create(mode, request, "query"));
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    assertThat(responseJson(create(mode, request, "query"))).isEqualTo(first);
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  void omittedKeysKeepIndependentLegacyCreations(Mode mode) throws Exception {
    var request = request("호환");
    var first = http(mode, request, null);
    var retry = http(mode, request, null);
    assertThat(first.status()).isEqualTo(201);
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isNotEqualTo(first.data());
    assertCounts(mode, 2);
    assertThat(count("work_command_receipts")).isZero();
    assertThat(count("work_command_receipt_memberships")).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "workTypeId",
        "title",
        "plannedStartDate",
        "plannedEndDate",
        "sourceOrchidGroupIds",
        "excludedOrchidGroupIds",
        "details",
        "worker",
        "memo"
      })
  void changedFieldsConflictBeforeResolvingCurrentTargets(String field) throws Exception {
    var request = request("최초");
    assertThat(http(Mode.BATCH, request, "field").status()).isEqualTo(201);
    ObjectNode changed = objectMapper.valueToTree(request);
    switch (field) {
      case "workTypeId" -> changed.put(field, -1);
      case "plannedStartDate", "plannedEndDate" -> changed.put(field, DATE.minusDays(1).toString());
      case "sourceOrchidGroupIds" ->
          changed.set(field, objectMapper.<JsonNode>valueToTree(List.of(-1)));
      case "excludedOrchidGroupIds" ->
          changed.set(field, objectMapper.<JsonNode>valueToTree(List.of(groupId)));
      case "details" ->
          changed.set(field, objectMapper.<JsonNode>valueToTree(Map.of("materialName", "새 자재")));
      default -> changed.put(field, "변경");
    }
    var before = snapshot();
    var response =
        post(
            Mode.BATCH.path,
            objectMapper.createObjectNode().set("operation", changed).toString(),
            Map.of("Idempotency-Key", "field"));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(409);
    assertThat(response.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @CsvSource({
    "SINGLE,empty",
    "SINGLE,blank",
    "SINGLE,long",
    "BATCH,empty",
    "BATCH,blank",
    "BATCH,long",
    "RECORD,empty",
    "RECORD,blank",
    "RECORD,long"
  })
  void invalidKeysCreateNoBusinessRows(Mode mode, String kind) throws Exception {
    String key =
        switch (kind) {
          case "empty" -> "";
          case "blank" -> " ";
          default -> "x".repeat(101);
        };
    var before = snapshot();
    var response = http(mode, request("잘못된 키"), key);
    assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
    assertThat(response.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void distinctKeysAndEndpointScopesRepresentIndependentWork() {
    for (Mode mode : Mode.values()) {
      var first = create(mode, request("독립"), "first").getFirst();
      var second = create(mode, request("독립"), "second").getFirst();
      assertThat(second.id()).isNotEqualTo(first.id());
    }
    assertThat(count("work_operations")).isEqualTo(6);
    assertThat(count("work_command_receipts")).isEqualTo(6);
    assertThat(count("work_command_receipt_memberships")).isEqualTo(6);
  }

  @Test
  void mixedVarietyBatchPreservesOrderedResultsAndCreationRelation() throws Exception {
    var request =
        new WorkOperationCreateRequest(
            repotTypeId,
            "분갈이",
            DATE,
            DATE.plusDays(1),
            WorkSourceScopeType.MANUAL_SELECTION,
            null,
            null,
            List.of(otherGroupId, groupId),
            Map.of(),
            "담당",
            null,
            List.of());
    var first = http(Mode.BATCH, request, "mixed");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(first.data()).hasSize(2);
    long id = first.data().get(0).path("id").asLong();
    assertThat(relations.get(id, WorkOperationRelationKind.CREATION_BATCH))
        .extracting(v -> v.id())
        .containsExactly(
            first.data().get(0).path("id").asLong(), first.data().get(1).path("id").asLong());
    var before = snapshot();
    assertThat(http(Mode.BATCH, request, "mixed").data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertThat(count("work_operations")).isEqualTo(2);
    assertThat(count("work_command_receipt_memberships")).isEqualTo(2);
  }

  @Test
  void mixedBatchMembershipFailureRollsBackEveryVarietyOperation() {
    var request =
        new WorkOperationCreateRequest(
            repotTypeId,
            "분갈이",
            DATE,
            DATE.plusDays(1),
            WorkSourceScopeType.MANUAL_SELECTION,
            null,
            null,
            List.of(otherGroupId, groupId),
            Map.of(),
            "담당",
            null,
            List.of());
    var before = snapshot();
    jdbc.execute(
        "alter table work_command_receipt_memberships add constraint test_mixed_membership_failure check (operation_id < 0)");
    try {
      assertThatThrownBy(() -> create(Mode.BATCH, request, "mixed-failure"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_mixed_membership_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute(
          "alter table work_command_receipt_memberships drop constraint test_mixed_membership_failure");
    }
    assertThat(create(Mode.BATCH, request, "mixed-failure")).hasSize(2);
    assertThat(count("work_operations")).isEqualTo(2);
    assertThat(count("work_command_receipt_memberships")).isEqualTo(2);
  }

  @Test
  void failedValidationReleasesIdentityForCorrectedRequest() {
    var before = snapshot();
    var invalid =
        new WorkOperationCreateRequest(
            workTypeId,
            "오류",
            DATE,
            DATE.minusDays(1),
            WorkSourceScopeType.MANUAL_SELECTION,
            null,
            null,
            List.of(groupId),
            Map.of(),
            "담당",
            null,
            List.of());
    assertThatThrownBy(() -> create(Mode.BATCH, invalid, "correct"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(create(Mode.BATCH, request("수정"), "correct")).hasSize(1);
  }

  @Test
  void workFingerprintKeepsExistingObjectOrderAndDecimalScaleContract() throws Exception {
    var body = (ObjectNode) objectMapper.<JsonNode>valueToTree(request("지문"));
    body.set("details", objectMapper.readTree("{\"amount\":1.00,\"materialName\":\"물\"}"));
    var first = post(Mode.SINGLE.path, body.toString(), Map.of("Idempotency-Key", "canonical"));
    assertThat(first.status()).isEqualTo(201);
    body.set("details", objectMapper.readTree("{\"materialName\":\"물\",\"amount\":1.0}"));
    var retry = post(Mode.SINGLE.path, body.toString(), Map.of("Idempotency-Key", "canonical"));
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
  }

  @ParameterizedTest
  @CsvSource({
    "SINGLE,false,false",
    "SINGLE,false,true",
    "SINGLE,true,false",
    "BATCH,false,false",
    "BATCH,false,true",
    "BATCH,true,false",
    "RECORD,false,false",
    "RECORD,false,true",
    "RECORD,true,false"
  })
  void concurrentCreatesWaitOnReceiptBeforeBusinessLocks(
      Mode mode, boolean rollback, boolean changed) throws Exception {
    var held = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () -> {
                try {
                  return new Outcome(
                      new TransactionTemplate(transactionManager)
                          .execute(
                              tx -> {
                                winnerPid.set(
                                    jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                                var response = create(mode, request("동시"), "race");
                                held.countDown();
                                await(resume);
                                if (rollback) throw new IllegalStateException("simulated rollback");
                                return response;
                              }),
                      null);
                } catch (RuntimeException error) {
                  return new Outcome(null, error);
                }
              });
      try {
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var loser =
            pool.submit(
                () -> {
                  try {
                    return new Outcome(
                        new TransactionTemplate(transactionManager)
                            .execute(
                                tx -> {
                                  jdbc.execute("set local lock_timeout = '15s'");
                                  loserPid.set(
                                      jdbc.queryForObject(
                                          "select pg_backend_pid()", Integer.class));
                                  started.countDown();
                                  return create(mode, request(changed ? "변경" : "동시"), "race");
                                }),
                        null);
                  } catch (RuntimeException error) {
                    return new Outcome(null, error);
                  }
                });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        boolean blocked = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "select ? = any(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).isTrue();
        assertThat(
                jdbc.queryForObject(
                    "select query from pg_stat_activity where pid = ?",
                    String.class,
                    loserPid.get()))
            .contains("work_command_receipts");
        assertThat(loser.isDone()).isFalse();
        resume.countDown();
        var first = winner.get(20, TimeUnit.SECONDS);
        var second = loser.get(20, TimeUnit.SECONDS);
        if (rollback) {
          assertThat(first.error()).isInstanceOf(IllegalStateException.class);
          assertThat(second.error()).isNull();
        } else if (changed) {
          assertThat(first.error()).isNull();
          assertThat(second.error()).isInstanceOf(ConflictException.class);
          assertThat(((ConflictException) second.error()).getCode())
              .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        } else {
          assertThat(first.error()).isNull();
          assertThat(second.error()).isNull();
          assertThat(responseJson(second.views())).isEqualTo(responseJson(first.views()));
        }
        assertCounts(mode, 1);
        assertThat(count("work_command_receipts")).isEqualTo(1);
        assertThat(count("work_command_receipt_memberships")).isEqualTo(1);
      } finally {
        resume.countDown();
      }
    }
  }

  private JsonNode responseJson(Object response) {
    try {
      return objectMapper.readTree(objectMapper.writeValueAsString(response));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(error);
    }
  }

  private void assertCounts(Mode mode, int expected) {
    assertThat(count("work_operations")).isEqualTo(expected);
    assertThat(count("work_operation_targets")).isEqualTo(expected);
    assertThat(count("work_target_executions")).isEqualTo(expected);
    assertThat(count("work_applied_effects")).isEqualTo(mode == Mode.RECORD ? expected : 0);
    if (mode == Mode.RECORD) {
      assertThat(jdbc.queryForList("select status from work_operations", String.class))
          .containsOnly("COMPLETED");
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
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
            "audit_events")) {
      result.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(r)::text from " + table + " r order by 1", String.class));
    }
    return result;
  }

  private ApiResult http(Mode mode, WorkOperationCreateRequest request, String key)
      throws Exception {
    Object body = mode == Mode.BATCH ? new WorkOperationBatchCreateRequest(request) : request;
    return post(
        mode.path,
        objectMapper.writeValueAsString(body),
        key == null ? Map.of() : Map.of("Idempotency-Key", key));
  }

  private List<WorkOperationView> create(
      Mode mode, WorkOperationCreateRequest request, String key) {
    return switch (mode) {
      case SINGLE -> List.of(plans.create(request, key));
      case BATCH -> plans.createBatch(new WorkOperationBatchCreateRequest(request), key);
      case RECORD -> List.of(plans.createCompletedRecord(request, key));
    };
  }

  private enum Mode {
    SINGLE("/api/work-operations"),
    BATCH("/api/work-operations/batch"),
    RECORD("/api/work-operations/record");
    private final String path;

    Mode(String path) {
      this.path = path;
    }
  }

  private record Outcome(List<WorkOperationView> views, RuntimeException error) {}

  private WorkOperationCreateRequest request(String title) {
    return new WorkOperationCreateRequest(
        workTypeId,
        title,
        DATE,
        DATE.plusDays(1),
        WorkSourceScopeType.MANUAL_SELECTION,
        null,
        null,
        List.of(groupId),
        Map.of("materialName", "물"),
        "작업 담당",
        null,
        List.of());
  }
}
