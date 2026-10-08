package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationType;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationQueryService;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.api.correction.WorkCorrectionCommand;
import com.greenhouse.backend.work.application.effect.WorkOrchidGroupLedgerRehearsalInspector;
import com.greenhouse.backend.work.application.operation.WorkOperationPlanService;
import com.greenhouse.backend.work.application.operation.WorkOperationVoidService;
import com.greenhouse.backend.work.spi.correction.WorkCorrectionPlan;
import com.greenhouse.backend.work.spi.correction.WorkCorrectionPort;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;

@Tag("work-e2e")
@org.springframework.test.context.TestPropertySource(
    properties = "features.work-quantity-correction.enabled=true")
class WorkCorrectionAuditPostgresE2ETest extends WorkE2ETestBase {

  @MockitoSpyBean WorkOperationVoidService cancellations;
  @MockitoSpyBean WorkOperationPlanService plans;
  @Autowired WorkCorrectionPort correctionPort;

  @Autowired WorkTestDataSeeder seeder;

  @Autowired JdbcTemplate jdbc;

  @Autowired DataSource dataSource;

  @Autowired OrchidGroupMutationQueryService mutationQuery;

  @Autowired OrchidGroupMutationGraphQueryService mutationGraph;

  @Autowired WorkOrchidGroupLedgerRehearsalInspector rehearsal;

  @Autowired OrchidGroupLedgerReconciliationService reconciliation;

  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;

  @Autowired OrchidGroupRepository groups;

  private long originalId;

  private List<Long> resultIds;

  @BeforeEach
  void prepare() throws Exception {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    var key = UUID.randomUUID();
    var date = LocalDate.of(2026, 8, 20);
    ledgerFixture.seedBaseline(key, date, "1.0.0");
    ledgerFixture.activate(key);
    var plan =
        post(
            "/api/work-operations",
            """
				{"workTypeId":%d,"title":"보정 대상","plannedStartDate":"2026-07-15",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]}
				"""
                .formatted(scenario.repotWorkTypeId(), scenario.orchidGroupId()));
    assertThat(plan.status()).as(plan.body().toString()).isEqualTo(201);
    originalId = plan.data().path("id").asLong();
    assertThat(post("/api/work-operations/" + originalId + "/start", "{}").status()).isEqualTo(200);
    var execution =
        post(
            "/api/work-operations/" + originalId + "/structure-change-executions",
            """
						{"idempotencyKey":"original","completedDate":"2026-07-15",
						 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":100}],
						 "results":[
						  {"bedZoneId":%d,"quantity":60,"potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":6,"endPosition":8},
						  {"bedZoneId":%d,"quantity":40,"potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":9,"endPosition":11}]}
						"""
                .formatted(scenario.orchidGroupId(), scenario.bedZoneId(), scenario.bedZoneId()));
    assertThat(execution.status()).as(execution.body().toString()).isEqualTo(201);
    resultIds =
        jdbc.queryForList(
            "SELECT id FROM orchid_groups WHERE id <> ? ORDER BY id",
            Long.class,
            scenario.orchidGroupId());
  }

  @Test
  void concurrentDuplicateRequestsCreateOneAuditAndOneMutation() throws Exception {
    String request = request("same-key", 55, "2026-07-15");
    var responses = parallel(request, request);
    assertThat(responses).extracting(ApiResult::status).containsOnly(201);
    assertThat(count("work_operation_corrections")).isEqualTo(1);
    assertThat(count("work_correction_receipts")).isEqualTo(1);
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(count("work_applied_effects")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE source_type = 'WORK_CORRECTION'",
                Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?",
                Integer.class,
                resultIds.getFirst()))
        .isEqualTo(55);
  }

  @Test
  void differentConcurrentRequestsSerializeAndPreserveBeforeValues() throws Exception {
    var responses =
        parallel(request("first", 55, "2026-07-15"), request("second", 50, "2026-07-15"));
    assertThat(responses).extracting(ApiResult::status).containsOnly(201);
    var history = get(path());
    var rows = history.data().path("corrections");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).path("adjustments").get(0).path("beforeQuantity").asInt()).isEqualTo(60);
    assertThat(rows.get(1).path("adjustments").get(0).path("beforeQuantity").asInt())
        .isEqualTo(rows.get(0).path("adjustments").get(0).path("afterQuantity").asInt());
    assertThat(count("work_operations")).isEqualTo(1);
  }

  @Test
  void retriesAfterCancellationReturnTheSavedAudit() throws Exception {
    String request = request("date", 60, "2026-07-14");
    assertThat(post(path(), request).status()).isEqualTo(201);
    var canceled =
        post(
            "/api/work-operations/" + originalId + "/cancel",
            """
				{"idempotencyKey":"cancel","reason":"실제로 하지 않은 작업"}
				""");
    assertThat(canceled.status()).as(canceled.body().toString()).isEqualTo(200);
    var retry = post(path(), request);
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(201);
    assertThat(retry.data().path("originalOperation").path("status").asText()).isEqualTo("VOIDED");
    assertThat(get("/api/work-operations/" + originalId + "/details").data().path("corrections"))
        .hasSize(1);
  }

  @Test
  void bulkCorrectionKeepsOneOriginalAndSupportsFiltersAndGraphs() throws Exception {
    String request =
        """
				{"idempotencyKey":"bulk","workDate":"2026-07-15","worker":"작업자","memo":"검수","reason":"수량 확인",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":55,"status":"정상"},
				  {"orchidGroupId":%d,"quantity":35,"status":"정상"}],
				 "quantityCorrections":[{"executionId":%d,"lossQuantity":10,"increaseQuantity":0}]}
				"""
            .formatted(resultIds.getFirst(), resultIds.getLast(), effectId());
    var result = post(path(), request);
    assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
    assertThat(result.data().path("originalOperation").path("status").asText())
        .isEqualTo("COMPLETED");
    assertThat(result.data().path("originalOperation").path("correctionCount").asLong())
        .isEqualTo(1);
    assertThat(result.data().path("corrections").get(0).path("adjustments")).hasSize(2);
    var filtered = get("/api/work-operations?hasCorrections=true");
    assertThat(filtered.data().path("totalElements").asLong()).isEqualTo(1);
    assertThat(
            filtered
                .data()
                .path("content")
                .get(0)
                .path("relationSummary")
                .path("linkedOperationCount")
                .asInt())
        .isZero();
    assertThat(
            get("/api/work-operations?hasCorrections=false").data().path("totalElements").asLong())
        .isZero();
    assertThat(
            get("/api/work-operations/calendar?from=2026-07-01&to=2026-07-31&hasCorrections=true")
                .data())
        .hasSize(1);
    var history =
        get(
            "/api/work-history?historyScopeType=ORCHID_GROUP&historyScopeId="
                + resultIds.getFirst());
    assertThat(history.data().path("totalElements").asLong()).isEqualTo(1);
    var graph = get("/api/work-operations/" + originalId + "/graph?detail=MUTATION");
    assertThat(graph.status()).as(graph.body().toString()).isEqualTo(200);
    long workNodes =
        StreamSupport.stream(graph.data().path("nodes").spliterator(), false)
            .filter(node -> node.path("nodeType").asText().equals("WORK_OPERATION"))
            .count();
    assertThat(workNodes).isEqualTo(1);
    var cancellation = get("/api/work-operations/" + originalId + "/cancel-eligibility");
    assertThat(cancellation.data().path("cancellable").asBoolean()).isFalse();
    assertThat(count("work_applied_effects")).isEqualTo(1);
  }

  @Test
  void failedBulkCorrectionRollsBackReceiptAuditAndAllQuantityChanges() throws Exception {
    jdbc.execute(
        "ALTER TABLE orchid_groups ADD CONSTRAINT test_correction_quantity CHECK (id <> "
            + resultIds.getLast()
            + " OR quantity >= 40)");
    String invalid =
        """
				{"idempotencyKey":"retry","workDate":"2026-07-15","reason":"수량 확인",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":55,"status":"정상"},
				  {"orchidGroupId":%d,"quantity":35,"status":"정상"}],
				 "quantityCorrections":[{"executionId":%d,"lossQuantity":10,"increaseQuantity":0}]}
				"""
            .formatted(resultIds.getFirst(), resultIds.getLast(), effectId());
    ApiResult failed;
    try {
      failed = post(path(), invalid);
    } finally {
      jdbc.execute("ALTER TABLE orchid_groups DROP CONSTRAINT test_correction_quantity");
    }
    assertThat(failed.status()).as(failed.body().toString()).isBetween(400, 599);
    assertThat(count("work_operation_corrections")).isZero();
    assertThat(count("work_correction_receipts")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?",
                Integer.class,
                resultIds.getFirst()))
        .isEqualTo(60);
    assertThat(post(path(), request("retry", 55, "2026-07-15")).status()).isEqualTo(201);
  }

  @Test
  void dateOnlyCorrectionHasNoMutationAndKeyReuseIsRejected() throws Exception {
    String request =
        """
				{"idempotencyKey":"date-only","workDate":"2026-07-14","reason":"날짜 정정","orchidGroupAdjustments":[]}
				""";
    assertThat(post(path(), request).status()).isEqualTo(201);
    String stored =
        jdbc.queryForObject(
            "select result_details::text from work_operation_corrections", String.class);
    var details = objectMapper.readTree(stored);
    assertThat(details.has("quantityBalances")).isFalse();
    assertThat(details.path("adjustments").size()).isZero();
    assertThat(details.has("beforeWorkDate")).isTrue();
    assertThat(details.has("afterWorkDate")).isTrue();
    assertThat(post(path(), request).status()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select result_details::text from work_operation_corrections", String.class))
        .isEqualTo(stored);
    assertThat(
            jdbc.queryForObject("SELECT mutation_id FROM work_operation_corrections", Long.class))
        .isNull();
    var conflict = post(path(), request.replace("2026-07-14", "2026-07-13"));
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
  }

  @Test
  void rehearsalVerifiesAuditMutationProvenanceAndDetectsBrokenLinks() throws Exception {
    assertThat(post(path(), request("ledger", 55, "2026-07-15")).status()).isEqualTo(201);
    var mutations =
        mutationQuery.getMutations(
            resultIds.getFirst(), OrchidGroupMutationType.CORRECTION, null, 0, 20);
    assertThat(mutations.content())
        .singleElement()
        .satisfies(
            mutation -> {
              assertThat(mutation.workOperation()).isNotNull();
              assertThat(mutation.workOperation().id()).isEqualTo(originalId);
            });
    var graph = mutationGraph.getGraph(resultIds.getFirst(), 0, 100);
    assertThat(graph.nodes())
        .filteredOn(
            node ->
                "CORRECTION"
                    .equals(node.mutationType() == null ? null : node.mutationType().name()))
        .singleElement()
        .satisfies(node -> assertThat(node.workOperation().id()).isEqualTo(originalId));
    var references = rehearsal.inspect().corrections();
    assertThat(references).hasSize(1);
    assertThat(references.getFirst().changesGroups()).isTrue();
    assertThat(references.getFirst().orchidGroupIds()).containsExactly(resultIds.getFirst());
    assertThat(reconciliation.reconcile().issues())
        .noneMatch(issue -> issue.code().contains("WORK_CORRECTION"));
    jdbc.update("UPDATE work_operation_corrections SET correlation_id = ?", UUID.randomUUID());
    assertThat(reconciliation.reconcile().issues())
        .anyMatch(issue -> issue.code().equals("INVALID_WORK_CORRECTION_MUTATION_LINK"));
  }

  @Test
  void rejectsReactivationIntoOccupiedPlacementAndRollsBackTheWholeCorrection() throws Exception {
    assertThat(post(path(), request("zero", 0, "2026-07-15")).status()).isEqualTo(201);
    Long zone =
        jdbc.queryForObject(
            "SELECT bed_zone_id FROM orchid_groups WHERE id=?", Long.class, resultIds.getFirst());
    Long variety =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id=?", Long.class, resultIds.getFirst());
    var occupant =
        post(
            "/api/orchid-groups",
            """
				{"bedZoneId":%d,"varietyId":%d,"quantity":10,"potSize":"4치","ageYear":3,
				 "status":"정상","startPosition":6,"endPosition":8}
				"""
                .formatted(zone, variety));
    assertThat(occupant.status()).as(occupant.body().toString()).isEqualTo(201);
    var before = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    long mutations = count("orchid_group_mutations");
    var failed =
        post(
            path(),
            """
				{"idempotencyKey":"occupied","workDate":"2026-07-14","reason":"보정",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":55,"status":"정상"},
				 {"orchidGroupId":%d,"quantity":35,"status":"정상"}],
				 "quantityCorrections":[{"executionId":%d,"lossQuantity":10,"increaseQuantity":0}]}
				"""
                .formatted(resultIds.getFirst(), resultIds.getLast(), effectId()));
    assertThat(failed.status()).isEqualTo(400);
    assertThat(failed.body().path("error").path("details").toString()).contains("겹칩니다");
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id")).isEqualTo(before);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutations);
    assertThat(count("work_operation_corrections")).isEqualTo(1);
    assertThat(count("work_correction_receipts")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT planned_start_date::text FROM work_operations WHERE id=?",
                String.class,
                originalId))
        .isEqualTo("2026-07-15");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void reactivatesAZeroQuantityResultWhenItsPlacementIsStillAvailable() throws Exception {
    assertThat(post(path(), request("zero", 0, "2026-07-15")).status()).isEqualTo(201);
    var response = post(path(), request("reactivate", 60, "2026-07-15"));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
    assertThat(count("work_operation_corrections")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?",
                Integer.class,
                resultIds.getFirst()))
        .isEqualTo(60);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void cannotForgeCreationCancellationThroughAnOrdinaryStatusCorrection() throws Exception {
    var before = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    long mutations = count("orchid_group_mutations");
    for (int quantity : List.of(0, 60)) {
      var failed =
          post(
              path(),
              """
					{"idempotencyKey":"forged-%d","workDate":"2026-07-15","reason":"보정",
					 "cancelResultCreation":false,
					 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":%d,"status":" 생성 취소 "}],
					 "quantityCorrections":[{"executionId":%d,"lossQuantity":%d,"increaseQuantity":0}]}
					"""
                  .formatted(quantity, resultIds.getFirst(), quantity, effectId(), 60 - quantity));
      assertThat(failed.status()).isEqualTo(400);
      assertThat(failed.body().path("error").path("details").toString()).contains("결과 생성 취소로 처리");
    }
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id")).isEqualTo(before);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutations);
    assertThat(count("work_operation_corrections")).isZero();
    assertThat(count("work_correction_receipts")).isZero();
    assertThat(post(path(), request("valid-after-failure", 55, "2026-07-15")).status())
        .isEqualTo(201);
  }

  @Test
  void explicitCreationCancellationStillCreatesTheDedicatedMutation() throws Exception {
    var response =
        post(
            path(),
            """
				{"idempotencyKey":"explicit-cancel","workDate":"2026-07-15","reason":"오생성",
				 "cancelResultCreation":true,
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":60,"status":"정상"}]}
				"""
                .formatted(resultIds.getFirst()));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
    assertThat(
            jdbc.queryForMap(
                "SELECT quantity,status FROM orchid_groups WHERE id=?", resultIds.getFirst()))
        .containsEntry("quantity", 0)
        .containsEntry("status", "생성 취소");
    assertThat(
            jdbc.queryForObject(
                """
				SELECT m.mutation_type FROM orchid_group_mutations m
				JOIN work_operation_corrections c ON c.mutation_id=m.id WHERE c.original_work_operation_id=?
				""",
                String.class,
                originalId))
        .isEqualTo("CANCEL_CREATION");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void farmCorrectionPhasesRequireTheCallingTransaction() throws Exception {
    var request =
        objectMapper.readValue(request("mandatory", 55, "2026-07-15"), WorkCorrectionCommand.class);
    assertThatThrownBy(() -> correctionPort.prepare(originalId, request))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThatThrownBy(() -> correctionPort.apply(1L, request, WorkCorrectionPlan.noChanges()))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(count("work_operation_corrections")).isZero();
    assertThat(count("work_correction_receipts")).isZero();
  }

  @Test
  void unchangedCorrectionRollsBackItsReceiptAndCanRetryWithAChangedDate() throws Exception {
    var groupsBefore = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    long mutationsBefore = count("orchid_group_mutations");
    String unchanged = request("no-change", 60, "2026-07-15");
    var failed = post(path(), unchanged);
    assertThat(failed.status()).as(failed.body().toString()).isEqualTo(400);
    assertThat(failed.body().toString()).contains("현재 값과 다른 보정 값이 필요");
    assertThat(count("work_operation_corrections")).isZero();
    assertThat(count("work_correction_receipts")).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id"))
        .isEqualTo(groupsBefore);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationsBefore);
    assertThat(post(path(), unchanged.replace("2026-07-15", "2026-07-14")).status()).isEqualTo(201);
    assertThat(count("work_operation_corrections")).isEqualTo(1);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationsBefore);
  }

  @Test
  void resultCreationCancellationCannotAlsoChangeTheWorkDate() throws Exception {
    String request =
        """
        {"idempotencyKey":"cancel-and-date","workDate":"2026-07-14","reason":"오생성",
         "cancelResultCreation":true,
         "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":60,"status":"정상"}]}
        """
            .formatted(resultIds.getFirst());
    var groupsBefore = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
    long mutationsBefore = count("orchid_group_mutations");
    var failed = post(path(), request);
    assertThat(failed.status()).as(failed.body().toString()).isEqualTo(400);
    assertThat(failed.body().toString()).contains("결과 생성 취소와 작업일 보정은 별도로 처리");
    assertThat(count("work_operation_corrections")).isZero();
    assertThat(count("work_correction_receipts")).isZero();
    assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id"))
        .isEqualTo(groupsBefore);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationsBefore);
    assertThat(post(path(), request.replace("2026-07-14", "2026-07-15")).status()).isEqualTo(201);
  }

  @Test
  void finalAuditFailureRollsBackMutationDateSnapshotsAndReceipt() throws Exception {
    var before = correctionState();
    String request = request("audit-completion-failure", 55, "2026-07-14");
    jdbc.execute(
        "ALTER TABLE work_operation_corrections ADD CONSTRAINT test_correction_completion CHECK (result_details = '{}'::jsonb)");
    ApiResult failed;
    try {
      failed = post(path(), request);
    } finally {
      jdbc.execute(
          "ALTER TABLE work_operation_corrections DROP CONSTRAINT test_correction_completion");
    }
    assertThat(failed.status()).as(failed.body().toString()).isBetween(400, 599);
    assertThat(correctionState()).isEqualTo(before);
    var retry = post(path(), request);
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(201);
    assertThat(retry.data().path("corrections").get(0).path("beforeWorkDate").asText())
        .isEqualTo("2026-07-15");
    assertThat(retry.data().path("corrections").get(0).path("afterWorkDate").asText())
        .isEqualTo("2026-07-14");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void dateOnlyDatabaseFailureRollsBackAuditAndAllowsTheSameKeyRetry() throws Exception {
    var before = correctionState();
    String request =
        """
        {"idempotencyKey":"date-db-failure","workDate":"2026-07-14","reason":"날짜 정정","orchidGroupAdjustments":[]}
        """;
    jdbc.execute(
        "ALTER TABLE work_operations ADD CONSTRAINT test_correction_date CHECK (id <> "
            + originalId
            + " OR planned_start_date = DATE '2026-07-15')");
    ApiResult failed;
    try {
      failed = post(path(), request);
    } finally {
      jdbc.execute("ALTER TABLE work_operations DROP CONSTRAINT test_correction_date");
    }
    assertThat(failed.status()).as(failed.body().toString()).isBetween(400, 599);
    assertThat(correctionState()).isEqualTo(before);
    var retry = post(path(), request);
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject("SELECT mutation_id FROM work_operation_corrections", Long.class))
        .isNull();
    assertThat(count("orchid_group_mutations"))
        .isEqualTo(((List<?>) before.get("mutations")).size());
  }

  @Test
  void concurrentDateAndQuantityCorrectionsPreserveAContinuousBeforeAfterHistory()
      throws Exception {
    var responses =
        parallel(request("date-first", 55, "2026-07-14"), request("date-second", 50, "2026-07-13"));
    assertThat(responses).extracting(ApiResult::status).containsOnly(201);
    var rows = get(path()).data().path("corrections");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).path("beforeWorkDate").asText()).isEqualTo("2026-07-15");
    assertThat(rows.get(1).path("beforeWorkDate")).isEqualTo(rows.get(0).path("afterWorkDate"));
    assertThat(rows.get(1).path("adjustments").get(0).path("beforeQuantity"))
        .isEqualTo(rows.get(0).path("adjustments").get(0).path("afterQuantity"));
    assertThat(get(path()).data().path("originalOperation").path("plannedStartDate"))
        .isEqualTo(rows.get(1).path("afterWorkDate"));
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Map<String, Object> correctionState() {
    var state = new LinkedHashMap<String, Object>();
    for (String table :
        List.of(
            "work_operations",
            "work_operation_corrections",
            "work_correction_receipts",
            "work_applied_effects",
            "orchid_groups",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "audit_events")) {
      String order = table.equals("work_correction_receipts") ? "request_key" : "id";
      state.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + order));
    }
    state.put("mutations", jdbc.queryForList("SELECT * FROM orchid_group_mutations ORDER BY id"));
    return state;
  }

  private List<ApiResult> parallel(String first, String second) throws Exception {
    return parallel(path(), first, path(), second);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  void cancellationAndNewWorkRegistrationSerializeOnTheResultRoot(boolean cancelFirst)
      throws Exception {
    long result = resultIds.getFirst();
    String cancelPath = "/api/work-operations/" + originalId + "/cancel";
    String cancelBody = "{\"idempotencyKey\":\"race-cancel\",\"reason\":\"오등록\"}";
    String planBody =
        """
				{"workTypeId":%d,"title":"동시 등록","plannedStartDate":"2026-07-15",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]}
				"""
            .formatted(
                jdbc.queryForObject("SELECT id FROM work_types WHERE code='PESTICIDE'", Long.class),
                result);
    var cancellationWorker = new PostgresLockTestSupport.Worker();
    var planWorker = new PostgresLockTestSupport.Worker();
    doAnswer(
            invocation -> {
              cancellationWorker.capture(jdbc);
              return invocation.callRealMethod();
            })
        .when(cancellations)
        .cancelOperation(anyLong(), any());
    doAnswer(
            invocation -> {
              planWorker.capture(jdbc);
              return invocation.callRealMethod();
            })
        .when(plans)
        .create(any(), nullable(String.class));
    try (var connection = dataSource.getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      int owner = PostgresLockTestSupport.backendPid(connection);
      try (var statement = connection.createStatement()) {
        statement
            .executeQuery("SELECT id FROM orchid_groups WHERE id=" + result + " FOR UPDATE")
            .close();
      }
      var first =
          executor.submit(
              () ->
                  post(
                      cancelFirst ? cancelPath : "/api/work-operations",
                      cancelFirst ? cancelBody : planBody));
      try {
        (cancelFirst ? cancellationWorker : planWorker).awaitBlockedBy(jdbc, owner, first);
        var second =
            executor.submit(
                () ->
                    post(
                        cancelFirst ? "/api/work-operations" : cancelPath,
                        cancelFirst ? planBody : cancelBody));
        (cancelFirst ? planWorker : cancellationWorker).awaitBlockedBy(jdbc, owner, second);
        connection.commit();
        var firstResult = first.get(20, TimeUnit.SECONDS);
        var secondResult = second.get(20, TimeUnit.SECONDS);
        assertThat(firstResult.status())
            .as(firstResult.body().toString())
            .isEqualTo(cancelFirst ? 200 : 201);
        assertThat(secondResult.status())
            .as(secondResult.body().toString())
            .isEqualTo(cancelFirst ? 409 : 400);
        if (cancelFirst) {
          assertThat(secondResult.body().path("error").path("code").asText())
              .isEqualTo("WORK_TARGET_CHANGED");
        }
      } finally {
        connection.rollback();
      }
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM work_operation_targets t JOIN work_operations w ON w.id=t.work_operation_id JOIN orchid_groups g ON g.id=t.orchid_group_id WHERE w.status='PLANNED' AND (g.quantity=0 OR g.status='생성 취소')",
                Long.class))
        .isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void concurrentReactivationAndCreationCannotBothOccupyTheSamePlacement() throws Exception {
    assertThat(post(path(), request("zero", 0, "2026-07-15")).status()).isEqualTo(201);
    Long zone =
        jdbc.queryForObject(
            "SELECT bed_zone_id FROM orchid_groups WHERE id=?", Long.class, resultIds.getFirst());
    Long variety =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id=?", Long.class, resultIds.getFirst());
    String create =
        """
				{"bedZoneId":%d,"varietyId":%d,"quantity":10,"potSize":"4치","ageYear":3,
				 "status":"정상","startPosition":6,"endPosition":8}
				"""
            .formatted(zone, variety);
    var results =
        parallel(path(), request("reactivate", 60, "2026-07-15"), "/api/orchid-groups", create);
    assertThat(results).extracting(ApiResult::status).containsExactlyInAnyOrder(201, 400);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private List<ApiResult> parallel(String firstPath, String first, String secondPath, String second)
      throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var requests = List.of(new String[] {firstPath, first}, new String[] {secondPath, second});
      var futures =
          requests.stream()
              .map(
                  request ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(5, TimeUnit.SECONDS))
                              throw new AssertionError("start timeout");
                            return post(request[0], request[1]);
                          }))
              .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          futures.getFirst().get(20, TimeUnit.SECONDS),
          futures.getLast().get(20, TimeUnit.SECONDS));
    }
  }

  private String request(String key, int quantity, String date) {
    return """
				{"idempotencyKey":"%s","workDate":"%s","reason":"수량 확인",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":%d,"status":"정상"}],
				 "quantityCorrections":[{"executionId":%d,"lossQuantity":%d,"increaseQuantity":%d}]}
				"""
        .formatted(
            key,
            date,
            resultIds.getFirst(),
            quantity,
            effectId(),
            Math.max(60 - quantity, 0),
            Math.max(quantity - 60, 0));
  }

  private long effectId() {
    return jdbc.queryForObject("SELECT min(id) FROM work_applied_effects", Long.class);
  }

  private String path() {
    return "/api/work-operations/" + originalId + "/corrections";
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }
}
