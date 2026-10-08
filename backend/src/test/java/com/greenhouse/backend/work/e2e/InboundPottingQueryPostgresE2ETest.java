package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.farm.inbound.application.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.inbound.application.InboundRecordService;
import com.greenhouse.backend.farm.inbound.domain.InboundType;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.farm.variety.repository.VarietyRepository;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.effect.web.dto.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.effect.web.dto.InboundPottingRecordCreateRequest;
import com.greenhouse.backend.work.operation.application.InboundPottingPlanService;
import com.greenhouse.backend.work.operation.application.WorkOperationProgressService;
import com.greenhouse.backend.work.operation.application.WorkOperationQueryService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
class InboundPottingQueryPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  private static final String RECORD = "/api/work-operations/inbound-potting-records";
  private static final String EXECUTE = "/api/work-operations/inbound-potting-executions";
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private InboundRecordService inbounds;
  @Autowired private InboundPottingPlanService plans;
  @Autowired private WorkOperationProgressService progress;
  @Autowired private VarietyRepository varieties;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @MockitoBean private Clock clock;
  @MockitoSpyBean private WorkOperationQueryService queries;
  private List<Long> ids;
  private List<Long> zones;

  @BeforeEach
  void seed() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T09:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    seeder.baselineGroups();
    long variety =
        jdbc.queryForObject(
            "select variety_id from orchid_groups where id = ?",
            Long.class,
            scenario.orchidGroupId());
    zones = jdbc.queryForList("select id from bed_zones order by id limit 8", Long.class);
    ids = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      ids.add(
          inbounds
              .create(
                  new InboundRecordCreateCommand(
                      DATE,
                      InboundType.FLASK_SEEDLING,
                      variety,
                      null,
                      20,
                      "배양실 " + i,
                      DATE,
                      null,
                      "입고 담당",
                      null))
              .id());
    }
  }

  @ParameterizedTest
  @CsvSource({
    "NEW,1",
    "NEW,8",
    "PLANNED,1",
    "PLANNED,8",
    "PAUSED,1",
    "PAUSED,8",
    "IN_PROGRESS,1",
    "IN_PROGRESS,8",
    "MULTI_VARIETY,8"
  })
  void recordAssemblesOnceForNewAndActivePlansAndPreservesReplay(String state, int size)
      throws Exception {
    if (state.equals("MULTI_VARIETY")) {
      long variety =
          varieties
              .saveAndFlush(
                  new Variety(
                      "QUERY-POTTING-B",
                      "팔레놉시스",
                      "두 번째 포트 품종",
                      null,
                      "3.5치",
                      true,
                      true,
                      null,
                      null))
              .getId();
      for (int i = 1; i < size; i += 2)
        jdbc.update("update inbound_records set variety_id = ? where id = ?", variety, ids.get(i));
    } else if (!state.equals("NEW")) {
      var plan = plans.create(plan(size));
      if (!state.equals("PLANNED")) progress.start(plan.id());
      if (state.equals("PAUSED")) progress.pause(plan.id());
    }
    var stats = statistics();
    stats.clear();
    String payload = payload(size);
    var first = post(RECORD, payload);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(report(state + "-" + size, stats)).isEqualTo(1);
    assertThat(first.data()).hasSize(state.equals("MULTI_VARIETY") ? 2 : 1);
    assertThat(first.data().get(0).path("targets").get(0).path("inboundRecordId").asLong())
        .isEqualTo(state.equals("MULTI_VARIETY") ? ids.get(1) : ids.get(0));
    for (var operation : first.data()) {
      assertThat(operation.path("status").asText()).isEqualTo("COMPLETED");
      assertThat(operation.path("progress").path("progressPercent").asInt()).isEqualTo(100);
      assertThat(
              operation
                  .path("targets")
                  .get(0)
                  .path("locationSnapshot")
                  .path("pottingDueDate")
                  .isTextual())
          .isTrue();
      assertThat(comparison(operation))
          .isEqualTo(
              comparison(get("/api/work-operations/" + operation.path("id").asLong()).data()));
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from work_applied_effects where handler_code = 'POTTING'",
                Integer.class))
        .isEqualTo(size);
    var before = snapshot();
    stats.clear();
    var replay = post(RECORD, payload);
    assertThat(replay.status()).isEqualTo(201);
    assertThat(comparison(replay.data())).isEqualTo(comparison(first.data()));
    assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void standaloneNewPlanAssemblesOnceAndReplays() throws Exception {
    var stats = statistics();
    stats.clear();
    var first = execute(0);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(report("standalone-new", stats)).isEqualTo(1);
    assertThat(first.data().path("status").asText()).isEqualTo("COMPLETED");
    var before = snapshot();
    assertThat(comparison(execute(0).data())).isEqualTo(comparison(first.data()));
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"FAILED", "PARTIALLY_COMPLETED", "SKIPPED", "CANCELED"})
  void siblingExecutionStateKeepsTheDomainCompletionDecision(String state) throws Exception {
    var plan = plans.create(plan(2));
    // Stored sibling states can exist before this immediate execution request.
    jdbc.update(
        "update work_target_executions set status = ?, processed_quantity = ? where work_operation_target_id = ?",
        state,
        state.equals("PARTIALLY_COMPLETED") ? 5 : 0,
        plan.targets().get(1).id());
    var result = execute(0);
    assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
    assertThat(result.data().path("status").asText())
        .isEqualTo(
            state.equals("SKIPPED") || state.equals("CANCELED") ? "COMPLETED" : "IN_PROGRESS");
  }

  @Test
  void partialSharedPlanRefreshesSnapshotAndResumesForTheLastTarget() throws Exception {
    var plan = plans.create(plan(2));
    jdbc.update(
        "update inbound_records set estimated_quantity = 37, temp_location = '변경된 배양실' where id = ?",
        ids.get(0));
    var first = execute(0);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(first.data().path("status").asText()).isEqualTo("IN_PROGRESS");
    assertThat(first.data().path("progress").path("pending").asInt()).isEqualTo(1);
    var target = first.data().path("targets").get(0);
    assertThat(target.path("quantitySnapshot").asInt()).isEqualTo(37);
    assertThat(target.path("locationSnapshot").path("tempLocation").asText()).isEqualTo("변경된 배양실");
    assertThat(target.path("resultDetails").path("actualQuantity").asInt()).isEqualTo(25);
    progress.pause(plan.id());
    var second = execute(1);
    assertThat(second.status()).as(second.body().toString()).isEqualTo(201);
    assertThat(second.data().path("id").asLong()).isEqualTo(plan.id());
    assertThat(second.data().path("status").asText()).isEqualTo("COMPLETED");
    assertThat(second.data().path("progress").path("completed").asInt()).isEqualTo(2);
    assertThat(comparison(second.data().path("targets").get(0))).isEqualTo(comparison(target));
  }

  @Test
  void legacyEffectAndNewExecutionKeepTheirOperationOrderAndRejectConflictingContent()
      throws Exception {
    var first = execute(0);
    assertThat(first.status()).isEqualTo(201);
    jdbc.update(
        "update work_applied_effects set effect_key = 'POTTING:query-pot-0', command_fingerprint = null where work_operation_id = ?",
        first.data().path("id").asLong());
    var recorded = post(RECORD, payload(2));
    assertThat(recorded.status()).as(recorded.body().toString()).isEqualTo(201);
    assertThat(recorded.data()).hasSize(2);
    assertThat(recorded.data().get(1).path("id").asLong())
        .isEqualTo(first.data().path("id").asLong());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from work_applied_effects where handler_code = 'POTTING'",
                Integer.class))
        .isEqualTo(2);
    jdbc.update("delete from work_command_receipts where receipt_key like 'POTTING_RECORD:%'");
    var before = snapshot();
    var altered = objectMapper.readTree(payload(2));
    ((ObjectNode) altered.path("executions").get(1)).put("memo", "different");
    var conflict = post(RECORD, altered.toString());
    assertThat(conflict.status()).isEqualTo(409);
    assertThat(conflict.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void latePlacementFailureRollsBackTheBatchAndAllowsTheSameKeys() throws Exception {
    var before = snapshot();
    var bad = objectMapper.readTree(payload(2));
    ((ObjectNode) bad.path("executions").get(1).path("results").get(0)).put("startPosition", 3);
    var failed = post(RECORD, bad.toString());
    assertThat(failed.status()).as(failed.body().toString()).isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(post(RECORD, payload(2)).status()).isEqualTo(201);
  }

  @Test
  void finalResponseFailureRollsBackFarmWorkReceiptAndAuditThenAllowsRetry() throws Exception {
    var before = snapshot();
    doAnswer(
            invocation -> {
              var result = invocation.callRealMethod();
              if (jdbc.queryForObject(
                      "select count(*) from inbound_records where status = 'PLACED'", Integer.class)
                  == 2) throw new IllegalStateException("final response failure");
              return result;
            })
        .when(queries)
        .getAll(anyCollection());
    assertThat(post(RECORD, payload(2)).status()).isEqualTo(500);
    assertThat(snapshot()).isEqualTo(before);
    reset(queries);
    assertThat(post(RECORD, payload(2)).status()).isEqualTo(201);
  }

  private InboundPottingPlanCreateRequest plan(int size) {
    return new InboundPottingPlanCreateRequest(
        "조회 회귀", DATE, DATE, ids.subList(0, size), "포트 담당", null);
  }

  private InboundPottingCommand command(int index) {
    return new InboundPottingCommand(
        "query-pot-" + index,
        ids.get(index),
        DATE,
        List.of(
            new InboundPottingResultInput(
                zones.get(index),
                25,
                "2인치",
                2,
                "POT",
                null,
                false,
                BigDecimal.valueOf(6),
                BigDecimal.valueOf(8),
                null)),
        "포트 담당",
        null);
  }

  private ApiResult execute(int index) throws Exception {
    return post(EXECUTE, objectMapper.writeValueAsString(command(index)));
  }

  private String payload(int size) throws Exception {
    return objectMapper.writeValueAsString(
        new InboundPottingRecordCreateRequest(
            plan(size), IntStream.range(0, size).mapToObj(i -> command(size - 1 - i)).toList()));
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private JsonNode comparison(JsonNode data) {
    var copy = data.deepCopy();
    // Existing JSONB snapshots read LocalDate as an array; fresh HTTP snapshots serialize it as
    // ISO.
    for (var location : copy.findValues("locationSnapshot")) {
      var date = location.path("pottingDueDate");
      if (date.isArray()) {
        assertThat(date).hasSize(3);
        String iso =
            LocalDate.of(date.get(0).intValue(), date.get(1).intValue(), date.get(2).intValue())
                .toString();
        ((ObjectNode) location).put("pottingDueDate", iso);
      }
    }
    return copy;
  }

  private long report(String scenario, Statistics stats) throws Exception {
    var counts = new LinkedHashMap<String, Long>();
    for (String query : stats.getQueries())
      counts.put(query, stats.getQueryStatistics(query).getExecutionCount());
    long details =
        counts.entrySet().stream()
            .filter(entry -> entry.getKey().contains("from WorkOperationCorrection c"))
            .mapToLong(Map.Entry::getValue)
            .sum();
    var path = Path.of("build/work-query-count/inbound-potting-" + scenario + ".json");
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
            "inbound_records",
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
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    }
    return result;
  }
}
