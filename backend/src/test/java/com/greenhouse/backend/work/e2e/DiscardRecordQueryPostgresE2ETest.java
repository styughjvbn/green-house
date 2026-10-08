package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.farm.variety.repository.VarietyRepository;
import com.greenhouse.backend.work.operation.application.WorkOperationQueryService;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("work-e2e")
class DiscardRecordQueryPostgresE2ETest extends WorkE2ETestBase {
  private static final String RECORD = "/api/work-operations/structure-change-records/batch";
  private static final String DISCARD = "/api/work-operations/discard-records";
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private VarietyRepository varieties;
  @MockitoBean private Clock clock;
  @MockitoSpyBean private WorkOperationQueryService queries;
  private List<Long> ids;
  private List<Long> zones;
  private long movementType;
  private long discardType;

  @BeforeEach
  void seed() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T09:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    movementType =
        jdbc.queryForObject("select id from work_types where code = 'MOVEMENT'", Long.class);
    discardType =
        jdbc.queryForObject("select id from work_types where code = 'DISCARD'", Long.class);
    zones = jdbc.queryForList("select id from bed_zones order by id limit 8", Long.class);
    ids = new ArrayList<>();
    ids.add(scenario.orchidGroupId());
    for (int i = 1; i < zones.size(); i++) {
      ids.add(
          jdbc.queryForObject(
              """
          insert into orchid_groups (created_at, updated_at, age_year, genus, placement_type,
            pot_size, pot_size_code, quantity, sort_order, status, variety_name, bed_zone_id,
            split_placement_allowed, variety_id, start_position, end_position, reserved_quantity)
          select created_at, updated_at, age_year, genus, placement_type,
            pot_size, pot_size_code, quantity, sort_order, status, variety_name, ?,
            split_placement_allowed, variety_id, start_position, end_position, reserved_quantity
          from orchid_groups where id = ? returning id
          """,
              Long.class,
              zones.get(i),
              scenario.orchidGroupId()));
    }
  }

  @ParameterizedTest
  @CsvSource({"1,false", "8,false", "1,true", "8,true"})
  void movementUsesOneFinalAssemblyAndKeepsLinkedDiscardReplayAndUndo(int size, boolean partial)
      throws Exception {
    seeder.baselineGroups();
    ObjectNode execution = execution(size, partial ? 30 : 100, 5);
    String path = RECORD;
    String request = record(size, execution);
    if (partial) {
      var planned = post("/api/work-operations", plan(size, movementType).toString());
      assertThat(planned.status()).as(planned.body().toString()).isEqualTo(201);
      long id = planned.data().path("id").asLong();
      assertThat(post("/api/work-operations/" + id + "/start", "").status()).isEqualTo(200);
      path = "/api/work-operations/" + id + "/structure-change-executions";
      request = execution.toString();
    }
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var first = post(path, request);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(report("movement-" + size + "-partial-" + partial)).isEqualTo(1);
    var movement = partial ? first.data() : first.data().get(0);
    long parent = movement.path("id").asLong();
    assertThat(movement.path("status").asText()).isEqualTo(partial ? "IN_PROGRESS" : "COMPLETED");
    long child =
        movement
            .path("targets")
            .get(0)
            .path("resultDetails")
            .path("discardWorkOperationId")
            .asLong();
    assertThat(child).isPositive();
    var discard = get("/api/work-operations/" + child);
    assertThat(discard.status()).isEqualTo(200);
    assertThat(discard.data().path("status").asText()).isEqualTo("COMPLETED");
    assertThat(discard.data().path("parentOperationId").asLong()).isEqualTo(parent);
    assertThat(discard.data().path("relationType").asText()).isEqualTo("MOVEMENT_DISCARD");
    assertThat(discard.data().path("title").asText()).isEqualTo("E2E 난 · 자리 이동 후 폐기");
    assertThat(discard.data().path("targets")).hasSize(size);
    assertThat(discard.data().path("details").path("allocationMethod").asText())
        .isEqualTo("PROPORTIONAL_BY_INPUT_QUANTITY");
    assertThat(discard.data().path("details").path("totalDiscardQuantity").asInt())
        .isEqualTo(5 * size);
    for (var target : discard.data().path("targets")) {
      long group = target.path("orchidGroupId").asLong();
      assertThat(target.path("resultDetails").path("discardedQuantity").asInt()).isEqualTo(5);
      assertThat(
              jdbc.queryForObject(
                  "select quantity from orchid_groups where id = ?", Integer.class, group))
          .isEqualTo(partial ? 70 : 0);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from work_applied_effects where work_operation_id = ?",
                Integer.class,
                child))
        .isEqualTo(size);
    assertThat(
            jdbc.queryForObject(
                "select jsonb_exists(result_details, 'discardWorkOperationId') from work_applied_effects where work_operation_id = ?",
                Boolean.class,
                parent))
        .isFalse();
    var beforeReplay = snapshot();
    assertThat(post(path, request).data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(beforeReplay);
    var childVoid =
        post(
            "/api/work-operations/" + child + "/void",
            "{\"idempotencyKey\":\"child-only\",\"reason\":\"test\"}");
    assertThat(childVoid.status()).as(childVoid.body().toString()).isEqualTo(400);
    var parentVoid =
        post(
            "/api/work-operations/" + parent + "/void",
            "{\"idempotencyKey\":\"parent-void\",\"reason\":\"test\"}");
    assertThat(parentVoid.status()).as(parentVoid.body().toString()).isEqualTo(200);
    assertThat(get("/api/work-operations/" + child).data().path("status").asText())
        .isEqualTo("VOIDED");
    for (long id : ids.subList(0, size))
      assertThat(
              jdbc.queryForObject(
                  "select quantity from orchid_groups where id = ?", Integer.class, id))
          .isEqualTo(100);
    var afterVoid = snapshot();
    assertThat(post(path, request).status()).isEqualTo(201);
    assertThat(snapshot()).isEqualTo(afterVoid);
  }

  @ParameterizedTest
  @CsvSource({"1,false", "8,false", "8,true"})
  void standaloneDiscardAssemblesOnceAndKeepsVarietyMembershipAndQuantities(int size, boolean mixed)
      throws Exception {
    if (mixed) mixedVarieties();
    seeder.baselineGroups();
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    String request = discard(size).toString();
    stats.clear();
    var result = post(DISCARD, request);
    assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
    assertThat(report("standalone-" + size + "-mixed-" + mixed)).isEqualTo(1);
    assertThat(result.data()).hasSize(mixed ? 2 : 1);
    for (var operation : result.data()) {
      assertThat(operation.path("status").asText()).isEqualTo("COMPLETED");
      assertThat(operation.path("parentOperationId").isNull()).isTrue();
      assertThat(operation)
          .isEqualTo(get("/api/work-operations/" + operation.path("id").asLong()).data());
      long variety =
          jdbc.queryForObject(
              "select variety_id from orchid_groups where id = ?",
              Long.class,
              operation.path("targets").get(0).path("orchidGroupId").asLong());
      for (var target : operation.path("targets")) {
        long group = target.path("orchidGroupId").asLong();
        assertThat(
                jdbc.queryForObject(
                    "select variety_id from orchid_groups where id = ?", Long.class, group))
            .isEqualTo(variety);
        assertThat(
                jdbc.queryForObject(
                    "select quantity from orchid_groups where id = ?", Integer.class, group))
            .isEqualTo(95);
        assertThat(target.path("resultDetails").path("discardedQuantity").asInt()).isEqualTo(5);
      }
    }
    assertThat(
            jdbc.queryForList(
                "select command_details ->> 'reason' from work_applied_effects where handler_code = 'DISCARD'",
                String.class))
        .containsOnly("상태 불량");
  }

  @Test
  void publicVarietyBatchPlanKeepsItsReceiptSnapshotAndUsesOneAssembly() throws Exception {
    mixedVarieties();
    seeder.baselineGroups();
    var request = objectMapper.createObjectNode().set("operation", plan(8, discardType)).toString();
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var first =
        post("/api/work-operations/batch", request, Map.of("Idempotency-Key", "discard-plans"));
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(report("public-variety-plan")).isEqualTo(1);
    assertThat(first.data()).hasSize(2);
    long id = first.data().get(0).path("id").asLong();
    jdbc.update("update work_operations set title = 'later' where id = ?", id);
    var before = snapshot();
    assertThat(
            post("/api/work-operations/batch", request, Map.of("Idempotency-Key", "discard-plans"))
                .data())
        .isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void zeroLossMovementCreatesNoDiscard() throws Exception {
    seeder.baselineGroups();
    var result = post(RECORD, record(1, execution(1, 100, 0)));
    assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
    assertThat(
            result
                .data()
                .get(0)
                .path("targets")
                .get(0)
                .path("resultDetails")
                .has("discardWorkOperationId"))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from work_operations where relation_type = 'MOVEMENT_DISCARD'",
                Integer.class))
        .isZero();
  }

  @Test
  void discardAllocationKeepsTheLowestIdTieBreak() throws Exception {
    seeder.baselineGroups();
    var execution = execution(2, 100, 5);
    ((ObjectNode) execution.path("results").get(0)).put("quantity", 98);
    ((ObjectNode) execution.path("results").get(1)).put("quantity", 99);
    var result = post(RECORD, record(2, execution));
    assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
    assertThat(
            jdbc.queryForList(
                "select (effect.result_details ->> 'discardedQuantity')::int from work_applied_effects effect join work_operation_targets target on target.id = effect.work_operation_target_id where effect.handler_code = 'DISCARD' order by target.orchid_group_id",
                Integer.class))
        .containsExactly(2, 1);
  }

  @Test
  void childEffectConstraintFailureRollsBackTheMovementAndAllowsTheSameKey() throws Exception {
    seeder.baselineGroups();
    String request = record(2, execution(2, 100, 5));
    var before = snapshot();
    jdbc.execute(
        "alter table work_applied_effects add constraint test_reject_child_effect check (handler_code <> 'DISCARD')");
    try {
      assertThat(post(RECORD, request).status()).isEqualTo(409);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table work_applied_effects drop constraint test_reject_child_effect");
    }
    assertThat(post(RECORD, request).status()).isEqualTo(201);
  }

  @Test
  void finalResponseFailureRollsBackTheMovementAndChildThenAllowsRetry() throws Exception {
    seeder.baselineGroups();
    String request = record(2, execution(2, 100, 5));
    var before = snapshot();
    doThrow(new IllegalStateException("final response failure"))
        .when(queries)
        .getAll(anyCollection());
    assertThat(post(RECORD, request).status()).isEqualTo(500);
    assertThat(snapshot()).isEqualTo(before);
    reset(queries);
    assertThat(post(RECORD, request).status()).isEqualTo(201);
  }

  @Test
  void finalResponseFailureRollsBackAllStandaloneVarietiesThenAllowsRetry() throws Exception {
    mixedVarieties();
    seeder.baselineGroups();
    String request = discard(8).toString();
    var before = snapshot();
    doThrow(new IllegalStateException("final response failure"))
        .when(queries)
        .getAll(anyCollection());
    assertThat(post(DISCARD, request).status()).isEqualTo(500);
    assertThat(snapshot()).isEqualTo(before);
    reset(queries);
    assertThat(post(DISCARD, request).status()).isEqualTo(201);
  }

  @Test
  void laterStandaloneDiscardFailureRollsBackEarlierEffects() throws Exception {
    seeder.baselineGroups();
    var before = snapshot();
    var bad = discard(2);
    ((ObjectNode) bad.path("results").get(1)).put("discardQuantity", 101);
    assertThat(post(DISCARD, bad.toString()).status()).isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(post(DISCARD, discard(2).toString()).status()).isEqualTo(201);
  }

  @ParameterizedTest
  @ValueSource(strings = {"DUPLICATE", "MISSING", "OTHER_TYPE"})
  void invalidStandaloneResultsPreserveValidationAndRollbackPlans(String kind) throws Exception {
    seeder.baselineGroups();
    var before = snapshot();
    var request = discard(2);
    if (kind.equals("DUPLICATE"))
      ((ObjectNode) request.path("results").get(1)).put("orchidGroupId", ids.get(0));
    if (kind.equals("MISSING")) ((ArrayNode) request.path("results")).remove(1);
    if (kind.equals("OTHER_TYPE"))
      ((ObjectNode) request.path("operation")).put("workTypeId", movementType);
    var failed = post(DISCARD, request.toString());
    assertThat(failed.status()).as(failed.body().toString()).isEqualTo(400);
    assertThat(failed.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  private void mixedVarieties() {
    var variety =
        varieties.saveAndFlush(
            new Variety(
                "QUERY-DISCARD-B", "팔레놉시스", "두 번째 폐기 품종", null, "3.5치", true, true, null, null));
    for (int i = 1; i < ids.size(); i += 2)
      jdbc.update(
          "update orchid_groups set variety_id = ?, variety_name = ? where id = ?",
          variety.getId(),
          variety.getName(),
          ids.get(i));
  }

  private ObjectNode plan(int size, long type) {
    var plan = objectMapper.createObjectNode();
    plan.put("workTypeId", type)
        .put("title", "폐기 조회 회귀")
        .put("plannedStartDate", "2026-10-04")
        .put("sourceScopeType", "MANUAL_SELECTION");
    var groups = plan.putArray("sourceOrchidGroupIds");
    ids.subList(0, size).forEach(groups::add);
    return plan;
  }

  private ObjectNode execution(int size, int input, int loss) {
    var request = objectMapper.createObjectNode();
    request
        .put("idempotencyKey", "discard-query-" + size + "-" + input)
        .put("completedDate", "2026-10-04")
        .put("worker", "이동 담당");
    var sources = request.putArray("sources");
    var results = request.putArray("results");
    for (int i = 0; i < size; i++) {
      sources.addObject().put("sourceOrchidGroupId", ids.get(i)).put("inputQuantity", input);
      results
          .addObject()
          .put("bedZoneId", zones.get(i))
          .put("quantity", input - loss)
          .put("attributeSourceOrchidGroupId", ids.get(i))
          .put("purpose", "NORMAL")
          .put("startPosition", 6)
          .put("endPosition", 8);
    }
    return request;
  }

  private String record(int size, ObjectNode execution) {
    var request = objectMapper.createObjectNode();
    var record = request.putArray("records").addObject();
    record.set("operation", plan(size, movementType));
    record.set("execution", execution);
    return request.toString();
  }

  private ObjectNode discard(int size) {
    var request = objectMapper.createObjectNode();
    request.set("operation", plan(size, discardType));
    request.put("completedDate", "2026-10-04").put("worker", "폐기 담당");
    var results = request.putArray("results");
    for (long id : ids.subList(0, size))
      results
          .addObject()
          .put("orchidGroupId", id)
          .put("discardQuantity", 5)
          .put("reason", " 상태 불량 ");
    return request;
  }

  private long report(String scenario) throws Exception {
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    var counts = new LinkedHashMap<String, Long>();
    for (String query : stats.getQueries())
      counts.put(query, stats.getQueryStatistics(query).getExecutionCount());
    long details =
        counts.entrySet().stream()
            .filter(entry -> entry.getKey().contains("from WorkOperationCorrection c"))
            .mapToLong(Map.Entry::getValue)
            .sum();
    var path = Path.of("build/work-query-count/discard-record-" + scenario + ".json");
    Files.createDirectories(path.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            path.toFile(),
            Map.of(
                "scenario",
                scenario,
                "preparedStatements",
                stats.getPrepareStatementCount(),
                "detailCorrectionQueries",
                details,
                "queries",
                counts));
    return details;
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
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    return result;
  }
}
