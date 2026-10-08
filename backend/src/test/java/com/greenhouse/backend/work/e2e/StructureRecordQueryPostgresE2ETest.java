package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("work-e2e")
class StructureRecordQueryPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @MockitoSpyBean private WorkOperationQueryService queries;
  @MockitoBean private Clock clock;
  private WorkTestDataSeeder.ContractScenario scenario;
  private List<Long> sources;
  private List<Long> zones;

  @BeforeEach
  void seed() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-04T09:00:00Z"));
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    seeder.resetKeepingSequences();
    scenario = seeder.seedContractScenario();
    zones = jdbc.queryForList("select id from bed_zones order by id limit 8", Long.class);
    sources = new ArrayList<>();
    sources.add(scenario.orchidGroupId());
    for (int i = 1; i < zones.size(); i++) {
      sources.add(
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
    seeder.baselineGroups();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 8})
  void batchAssemblesDetailsOnceAndReplaysWithoutRepeatingWrites(int size) throws Exception {
    String request = batch(size);
    var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    var first = post("/api/work-operations/structure-change-records/batch", request);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    long statements = stats.getPrepareStatementCount();
    var counts = new LinkedHashMap<String, Long>();
    for (String query : stats.getQueries()) {
      counts.put(query, stats.getQueryStatistics(query).getExecutionCount());
    }
    long detailCount =
        counts.entrySet().stream()
            .filter(entry -> entry.getKey().contains("from WorkOperationCorrection c"))
            .mapToLong(Map.Entry::getValue)
            .sum();
    var report = Path.of("build/work-query-count/structure-record-" + size + ".json");
    Files.createDirectories(report.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            report.toFile(),
            Map.of(
                "records",
                size,
                "preparedStatements",
                statements,
                "detailCorrectionQueries",
                detailCount,
                "queries",
                counts));
    // This aggregate belongs to detail assembly, so it must not run for intermediate write steps.
    assertThat(detailCount).isEqualTo(1);
    assertThat(
            counts.entrySet().stream()
                .filter(
                    entry -> {
                      String query = entry.getKey().toLowerCase(Locale.ROOT);
                      return query.contains("from workoperationtarget ")
                          && query.contains(".workoperation.id in ")
                          && query.contains(".excludedat is null");
                    })
                .mapToLong(Map.Entry::getValue)
                .sum())
        .isEqualTo(1);
    assertThat(first.data()).hasSize(size);
    for (int i = 0; i < size; i++) {
      var operation = first.data().get(i);
      assertThat(operation.path("status").asText()).isEqualTo("COMPLETED");
      assertThat(operation.path("progress").path("completed").asInt()).isEqualTo(1);
      assertThat(operation.path("progress").path("progressPercent").asInt()).isEqualTo(100);
      assertThat(operation.path("targets").get(0).path("orchidGroupId").asLong())
          .isEqualTo(sources.get(size - 1 - i));
      assertThat(operation)
          .isEqualTo(get("/api/work-operations/" + operation.path("id").asLong()).data());
    }
    var beforeReplay = snapshot();
    stats.clear();
    var replay = post("/api/work-operations/structure-change-records/batch", request);
    assertThat(replay.status()).isEqualTo(201);
    assertThat(replay.data()).isEqualTo(first.data());
    assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    assertThat(snapshot()).isEqualTo(beforeReplay);
  }

  @Test
  void finalResponseFailureRollsBackAllRecordsAndAllowsTheSameKeys() throws Exception {
    var before = snapshot();
    doThrow(new IllegalStateException("final response failure"))
        .when(queries)
        .getAll(anyCollection());
    var failed = post("/api/work-operations/structure-change-records/batch", batch(2));
    assertThat(failed.status()).isEqualTo(500);
    assertThat(snapshot()).isEqualTo(before);
    reset(queries);
    var retried = post("/api/work-operations/structure-change-records/batch", batch(2));
    assertThat(retried.status()).as(retried.body().toString()).isEqualTo(201);
    assertThat(retried.data()).hasSize(2);
  }

  @Test
  void unsupportedRecordTypeRollsBackThePlanAndReceipt() throws Exception {
    var before = snapshot();
    var failed =
        post(
            "/api/work-operations/structure-change-records/batch",
            batch(1)
                .replace(
                    "\"workTypeId\":" + scenario.repotWorkTypeId(),
                    "\"workTypeId\":" + scenario.pesticideWorkTypeId()));
    assertThat(failed.status()).isEqualTo(400);
    assertThat(failed.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void compatibilitySingleRecordKeepsTheSameDetailsAndReplay() throws Exception {
    var record = objectMapper.readTree(batch(1)).path("records").get(0).toString();
    var first = post("/api/work-operations/structure-change-records", record);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(first.data().path("status").asText()).isEqualTo("COMPLETED");
    assertThat(first.data())
        .isEqualTo(get("/api/work-operations/" + first.data().path("id").asLong()).data());
    var before = snapshot();
    assertThat(post("/api/work-operations/structure-change-records", record).data())
        .isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  private String batch(int size) throws Exception {
    List<JsonNode> records = IntStream.range(0, size).mapToObj(i -> record(size - 1 - i)).toList();
    return objectMapper.writeValueAsString(Map.of("records", records));
  }

  private JsonNode record(int index) {
    var record = objectMapper.createObjectNode();
    var plan = record.putObject("operation");
    plan.put("workTypeId", scenario.repotWorkTypeId());
    plan.put("title", "query regression " + index);
    plan.put("plannedStartDate", "2026-07-15");
    plan.put("sourceScopeType", "MANUAL_SELECTION");
    plan.putArray("sourceOrchidGroupIds").add(sources.get(index));
    var execution = record.putObject("execution");
    execution.put("idempotencyKey", "query-record-" + index);
    execution.put("completedDate", "2026-07-15");
    var source = execution.putArray("sources").addObject();
    source.put("sourceOrchidGroupId", sources.get(index));
    source.put("inputQuantity", 100);
    var result = execution.putArray("results").addObject();
    result.put("bedZoneId", zones.get(index));
    result.put("quantity", 100);
    result.putArray("sourceOrchidGroupIds").add(sources.get(index));
    result.put("potSize", "4치");
    result.put("ageYear", 3);
    result.put("purpose", "NORMAL");
    result.put("startPosition", 6);
    result.put("endPosition", 8);
    return record;
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
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    }
    return result;
  }
}
