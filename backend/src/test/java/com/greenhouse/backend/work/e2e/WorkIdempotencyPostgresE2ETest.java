package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.orchid.application.OrchidGroupReconciliationService;
import com.greenhouse.backend.farm.orchid.web.dto.OrchidGroupReconciliationRequest;
import com.greenhouse.backend.farm.transformation.web.dto.RepotWorkOperationRequest;
import com.greenhouse.backend.work.operation.application.WorkRequestFingerprint;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkIdempotencyPostgresE2ETest extends WorkE2ETestBase {

  @Autowired WorkTestDataSeeder seeder;

  @Autowired JdbcTemplate jdbc;

  @Autowired WorkRequestFingerprint fingerprints;

  @Autowired OrchidGroupReconciliationService reconciliationService;

  private WorkTestDataSeeder.ContractScenario scenario;

  @BeforeEach
  void prepare() {
    seeder.reset();
    scenario = seeder.seedContractScenario();
    seeder.baselineGroups();
  }

  @Test
  void completedStructureExecutionReplaysAndRejectsChangedContents() throws Exception {
    long operationId = plan();
    String path = "/api/work-operations/" + operationId + "/structure-change-executions";
    String request = execution("complete", scenario.orchidGroupId(), 100, 6);
    var first = post(path, request);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(first.data().path("status").asText()).isEqualTo("COMPLETED");
    assertSameResults(first, post(path, request));
    conflict(post(path, request.replace("\"quantity\":100", "\"quantity\":99")));
    assertThat(count("work_applied_effects")).isEqualTo(1);
    assertThat(quantity()).isZero();
  }

  @Test
  void partialReplayNormalizesNumericRepresentationAndChecksLegacyEffectContents()
      throws Exception {
    long operationId = plan();
    String path = "/api/work-operations/" + operationId + "/structure-change-executions";
    String request = execution("partial", scenario.orchidGroupId(), 40, 6);
    assertThat(post(path, request).status()).isEqualTo(201);
    jdbc.update("UPDATE work_applied_effects SET command_fingerprint = NULL");
    var replay = post(path, request.replace("\"startPosition\":6", "\"startPosition\":6.00"));
    assertThat(replay.status()).as(replay.body().toString()).isEqualTo(201);
    assertThat(replay.data().path("targets").get(0).path("processedQuantity").asInt())
        .isEqualTo(40);
    conflict(post(path, request.replace("\"quantity\":40", "\"quantity\":39")));
    assertThat(quantity()).isEqualTo(60);
    assertThat(count("work_applied_effects")).isEqualTo(1);
  }

  @Test
  void concurrentImmediateRequestsCreateOneOperationAndOneEffect() throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(1, 2).stream()
              .map(
                  index ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(5, TimeUnit.SECONDS))
                              throw new AssertionError("start timeout");
                            return post("/api/work-operations/repot", immediate("concurrent", 6));
                          }))
              .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var first = futures.getFirst().get(15, TimeUnit.SECONDS);
      var second = futures.getLast().get(15, TimeUnit.SECONDS);
      assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
      assertThat(second.status()).as(second.body().toString()).isEqualTo(201);
      assertSameResults(first, second);
    }
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(count("work_applied_effects")).isEqualTo(1);
    assertThat(count("work_command_receipts")).isEqualTo(1);
    assertThat(count("work_command_receipt_memberships")).isEqualTo(1);
    assertThat(quantity()).isEqualTo(60);
  }

  @Test
  void failedImmediateExecutionRollsBackItsReceiptAndAllowsCorrectedRetry() throws Exception {
    var invalid = post("/api/work-operations/repot", immediate("retry", 1));
    assertThat(invalid.status()).as(invalid.body().toString()).isEqualTo(400);
    assertThat(count("work_command_receipts")).isZero();
    assertThat(count("work_command_receipt_memberships")).isZero();
    assertThat(count("work_operations")).isZero();
    String valid = immediate("retry", 6);
    assertThat(post("/api/work-operations/repot", valid).status()).isEqualTo(201);
    conflict(
        post("/api/work-operations/repot", valid.replace("idempotency test", "changed title")));
    assertThat(quantity()).isEqualTo(60);
  }

  @Test
  void typedLegacyRepotReplaysTheOriginalDtoReceiptFingerprint() throws Exception {
    String request = immediate("payload-compat", 6);
    var first = post("/api/work-operations/repot", request);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    long operationId = first.data().path("operation").path("id").asLong();
    var oldDto = objectMapper.readValue(request, RepotWorkOperationRequest.class);
    assertOldReceiptFingerprint("payload-compat", "REPOT", operationId, oldDto);
    var snapshots =
        jdbc.queryForMap(
            "SELECT command_details::text, result_details::text, command_fingerprint FROM work_applied_effects WHERE work_operation_id = ?",
            operationId);
    assertSameResults(first, post("/api/work-operations/repot", request));
    assertThat(
            jdbc.queryForMap(
                "SELECT command_details::text, result_details::text, command_fingerprint FROM work_applied_effects WHERE work_operation_id = ?",
                operationId))
        .isEqualTo(snapshots);
    conflict(
        post(
            "/api/work-operations/repot", request.replace("idempotency test", "changed metadata")));
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(count("work_applied_effects")).isEqualTo(1);
    assertThat(quantity()).isEqualTo(60);
  }

  @Test
  void typedReconciliationKeepsOldReceiptSnapshotsAndTheHeldHttpPolicy() throws Exception {
    var request = reconciliationRequest();
    var first = reconciliationService.reconcile(scenario.orchidGroupId(), request);
    assertOldReceiptFingerprint("reconciliation-payload", "RECONCILIATION", first.id(), request);
    var snapshots =
        jdbc.queryForMap(
            "SELECT command_details::text, result_details::text FROM work_applied_effects WHERE work_operation_id = ?",
            first.id());
    var replay = reconciliationService.reconcile(scenario.orchidGroupId(), request);
    assertThat(replay.id()).isEqualTo(first.id());
    assertThat(
            jdbc.queryForMap(
                "SELECT command_details::text, result_details::text FROM work_applied_effects WHERE work_operation_id = ?",
                first.id()))
        .isEqualTo(snapshots);
    var changed =
        new OrchidGroupReconciliationRequest(
            request.idempotencyKey(),
            "changed title",
            request.workDate(),
            request.worker(),
            request.memo(),
            request.reason(),
            request.actualQuantity(),
            request.actualStatus(),
            request.actualBedZoneId(),
            request.actualStartPosition(),
            request.actualEndPosition());
    assertThatThrownBy(() -> reconciliationService.reconcile(scenario.orchidGroupId(), changed))
        .isInstanceOfSatisfying(
            ConflictException.class,
            exception -> assertThat(exception.getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED"));
    var held =
        post(
            "/api/orchid-groups/" + scenario.orchidGroupId() + "/reconciliations",
            objectMapper.writeValueAsString(request));
    assertThat(held.status()).isEqualTo(409);
    assertThat(held.body().path("error").path("code").asText()).isEqualTo("FEATURE_ON_HOLD");
    assertThat(quantity()).isEqualTo(90);
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(count("work_applied_effects")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type = 'RECONCILIATION'",
                Long.class))
        .isEqualTo(1);
  }

  @Test
  void typedReconciliationEffectFailureRollsBackQuantityMutationAndReceipt() {
    var request = reconciliationRequest();
    var tables =
        List.of(
            "work_operations",
            "work_operation_targets",
            "work_target_executions",
            "work_applied_effects",
            "work_command_receipts",
            "work_command_receipt_memberships",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "audit_events");
    var beforeCounts = new LinkedHashMap<String, Long>();
    tables.forEach(table -> beforeCounts.put(table, count(table)));
    var beforeGroup =
        jdbc.queryForMap(
            "SELECT quantity, reserved_quantity, state_revision, status, bed_zone_id, start_position, end_position FROM orchid_groups WHERE id = ?",
            scenario.orchidGroupId());
    jdbc.execute(
        "ALTER TABLE work_applied_effects ADD CONSTRAINT test_payload_effect_failure CHECK (handler_code <> 'RECONCILIATION')");
    try {
      assertThatThrownBy(() -> reconciliationService.reconcile(scenario.orchidGroupId(), request))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      jdbc.execute("ALTER TABLE work_applied_effects DROP CONSTRAINT test_payload_effect_failure");
    }
    assertThat(quantity()).isEqualTo(100);
    beforeCounts.forEach(
        (table, expected) -> assertThat(count(table)).as(table).isEqualTo(expected));
    assertThat(
            jdbc.queryForMap(
                "SELECT quantity, reserved_quantity, state_revision, status, bed_zone_id, start_position, end_position FROM orchid_groups WHERE id = ?",
                scenario.orchidGroupId()))
        .isEqualTo(beforeGroup);
    reconciliationService.reconcile(scenario.orchidGroupId(), request);
    assertThat(quantity()).isEqualTo(90);
    assertThat(count("work_command_receipts")).isEqualTo(1);
    assertThat(count("orchid_group_mutations"))
        .isEqualTo(beforeCounts.get("orchid_group_mutations") + 1);
  }

  @Test
  void batchReceiptPreservesTheOrderedOperationIds() throws Exception {
    long secondId = secondGroup();
    seeder.baselineGroups();
    String batch =
        "{\"records\":["
            + record("batch-a", scenario.orchidGroupId(), 6)
            + ","
            + record("batch-b", secondId, 16)
            + "]}";
    String path = "/api/work-operations/structure-change-records/batch";
    var first = post(path, batch);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(first.data()).hasSize(2);
    var replay = post(path, batch);
    assertThat(replay.status()).as(replay.body().toString()).isEqualTo(201);
    assertSameResults(first, replay);
    conflict(post(path, batch.replace("idempotency test", "different title")));
    assertThat(count("work_operations")).isEqualTo(2);
    assertThat(count("work_applied_effects")).isEqualTo(2);
    assertThat(count("work_command_receipt_memberships")).isEqualTo(2);
  }

  @Test
  void aFailureInTheSecondBatchRecordRollsBackAllResultsAndTheReceipt() throws Exception {
    long secondId = secondGroup();
    seeder.baselineGroups();
    String batch =
        "{\"records\":["
            + record("rollback-a", scenario.orchidGroupId(), 6)
            + ","
            + record("rollback-b", secondId, 6)
            + "]}";
    var result = post("/api/work-operations/structure-change-records/batch", batch);
    assertThat(result.status()).as(result.body().toString()).isEqualTo(400);
    assertThat(count("work_command_receipts")).isZero();
    assertThat(count("work_command_receipt_memberships")).isZero();
    assertThat(count("work_operations")).isZero();
    assertThat(quantity()).isEqualTo(100);
  }

  @Test
  void legacyImmediateReceiptWithoutOriginalPayloadFailsClosed() throws Exception {
    String request = immediate("legacy", 6);
    assertThat(post("/api/work-operations/repot", request).status()).isEqualTo(201);
    jdbc.update("UPDATE work_command_receipts SET request_fingerprint = NULL");
    var retry = post("/api/work-operations/repot", request);
    assertThat(retry.status()).isEqualTo(409);
    assertThat(retry.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_REPLAY_UNAVAILABLE");
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(quantity()).isEqualTo(60);
  }

  @Test
  void databaseRejectsSameEffectIdentityWithADifferentKind() throws Exception {
    assertThat(post("/api/work-operations/repot", immediate("unique", 6)).status()).isEqualTo(201);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
				INSERT INTO work_applied_effects (id, work_operation_id, effect_key, effect_kind, handler_code,
				    applied_at, created_at, updated_at)
				SELECT nextval('work_applied_effects_id_seq'), work_operation_id, effect_key, 'ATTRIBUTE_CHANGE',
				    handler_code, applied_at, created_at, updated_at FROM work_applied_effects LIMIT 1
				"""))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("work_applied_effects")).isEqualTo(1);
  }

  private long plan() throws Exception {
    var result = post("/api/work-operations", operation(scenario.orchidGroupId()));
    assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
    long id = result.data().path("id").asLong();
    assertThat(post("/api/work-operations/" + id + "/start", "").status()).isEqualTo(200);
    return id;
  }

  private OrchidGroupReconciliationRequest reconciliationRequest() {
    return new OrchidGroupReconciliationRequest(
        " reconciliation-payload ",
        "original title",
        LocalDate.of(2026, 7, 15),
        "tester",
        null,
        " 현장 실사 ",
        90,
        "관리",
        scenario.bedZoneId(),
        BigDecimal.ZERO,
        new BigDecimal("5"));
  }

  private void assertOldReceiptFingerprint(String key, String type, long operationId, Object oldDto)
      throws Exception {
    var operation =
        jdbc.queryForMap(
            "SELECT title, worker, memo, details, planned_start_date FROM work_operations WHERE id = ?",
            operationId);
    var oldEnvelope = new LinkedHashMap<String, Object>();
    oldEnvelope.put("workTypeCode", type);
    oldEnvelope.put("title", operation.get("title"));
    oldEnvelope.put("workDate", ((Date) operation.get("planned_start_date")).toLocalDate());
    oldEnvelope.put("worker", operation.get("worker"));
    oldEnvelope.put("memo", operation.get("memo"));
    oldEnvelope.put("orchidGroupId", scenario.orchidGroupId());
    oldEnvelope.put(
        "details",
        objectMapper.readValue(
            operation.get("details").toString(), new TypeReference<Map<String, Object>>() {}));
    oldEnvelope.put("payload", oldDto);
    String oldFingerprint = fingerprints.calculate(oldEnvelope);
    assertThat(
            jdbc.queryForObject(
                "SELECT request_fingerprint FROM work_command_receipts WHERE receipt_key = ?",
                String.class,
                "IMMEDIATE:" + key))
        .isEqualTo(oldFingerprint);
    jdbc.update(
        "UPDATE work_command_receipts SET request_fingerprint = ? WHERE receipt_key = ?",
        oldFingerprint,
        "IMMEDIATE:" + key);
  }

  private String operation(long groupId) {
    return """
				{"workTypeId":%d,"title":"idempotency test","plannedStartDate":"2026-07-15",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]}
				"""
        .formatted(scenario.repotWorkTypeId(), groupId);
  }

  private String record(String key, long groupId, int position) {
    return "{\"operation\":"
        + operation(groupId)
        + ",\"execution\":"
        + execution(key, groupId, 100, position)
        + "}";
  }

  private String execution(String key, long groupId, int quantity, int position) {
    return """
				{"idempotencyKey":"%s","completedDate":"2026-07-15","worker":"tester",
				 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":%d}],
				 "results":[{"bedZoneId":%d,"quantity":%d,"potSize":"4치","ageYear":3,
				 "purpose":"NORMAL","startPosition":%d,"endPosition":%d}]}
				"""
        .formatted(key, groupId, quantity, scenario.bedZoneId(), quantity, position, position + 2);
  }

  private String immediate(String key, int position) {
    return """
				{"idempotencyKey":"%s","title":"idempotency test","workDate":"2026-07-15",
				 "worker":"tester","sourceOrchidGroupId":%d,"inputQuantity":40,"lossQuantity":0,
				 "results":[{"bedZoneId":%d,"quantity":40,"potSize":"4치","ageYear":3,
				 "startPosition":%d,"endPosition":%d}]}
				"""
        .formatted(key, scenario.orchidGroupId(), scenario.bedZoneId(), position, position + 2);
  }

  private long secondGroup() {
    return jdbc.queryForObject(
        """
				INSERT INTO orchid_groups (id, created_at, updated_at, age_year, genus, placement_type, pot_size,
				 pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id, split_placement_allowed,
				 variety_id, start_position, end_position, reserved_quantity)
				SELECT nextval('orchid_groups_id_seq'), created_at, updated_at, age_year, genus, placement_type,
				 pot_size, pot_size_code, quantity, 2, status, variety_name, bed_zone_id, split_placement_allowed,
				 variety_id, 10, 15, 0 FROM orchid_groups WHERE id = ? RETURNING id
				""",
        Long.class,
        scenario.orchidGroupId());
  }

  private void assertSameResults(ApiResult first, ApiResult replay) {
    assertThat(replay.status()).isEqualTo(first.status());
    assertThat(replay.data().findValues("id")).isEqualTo(first.data().findValues("id"));
    assertThat(replay.data().findValues("resultOrchidGroupIds"))
        .isEqualTo(first.data().findValues("resultOrchidGroupIds"));
    assertThat(replay.data().findValues("processedQuantity"))
        .isEqualTo(first.data().findValues("processedQuantity"));
    assertThat(replay.data().findValues("status")).isEqualTo(first.data().findValues("status"));
  }

  private void conflict(ApiResult result) {
    assertThat(result.status()).as(result.body().toString()).isEqualTo(409);
    assertThat(result.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private int quantity() {
    return jdbc.queryForObject(
        "SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, scenario.orchidGroupId());
  }
}
