package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.greenhouse.backend.farm.transformation.application.StructureChangeExecutor;
import com.greenhouse.backend.work.api.effect.WorkExecutionResult;
import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import com.greenhouse.backend.work.operation.domain.WorkType;
import com.greenhouse.backend.work.operation.repository.WorkTypeRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("work-e2e")
class StructureLineageContractPostgresE2ETest extends WorkE2ETestBase {
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private WorkTypeRepository types;
  @Autowired private JdbcTemplate jdbc;
  @MockitoSpyBean private StructureChangeExecutor executor;
  private WorkTestDataSeeder.ContractScenario scenario;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    jdbc.update(
        "update work_types set is_active = true, name = code where code in ('MOVEMENT', 'REPOT', 'DIVIDE', 'MERGE')");
    scenario = seeder.seedContractScenario();
    seeder.baselineGroups();
  }

  @ParameterizedTest
  @CsvSource({
    "MOVEMENT,MOVEMENT,MOVED_TO",
    "MOVEMENT,MOVE,MOVED_TO",
    "REPOT,REPOT,REPOTTED_TO",
    "DIVIDE,DIVIDE,SPLIT_TO",
    "MERGE,MERGE,MERGED_TO"
  })
  void executionDetailsLineageVoidAndReplayUseTheStoredHandlerContract(
      String type, String storedHandler, String relation) throws Exception {
    long operationId = plan(type);
    var first = execute(operationId);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    long resultId =
        jdbc.queryForObject(
            "select orchid_group_id from work_effect_orchid_groups where relation_type = 'RESULT' order by id desc limit 1",
            Long.class);
    // Simulate a supported old effect code without rewriting Mutation/Entry or current group facts.
    jdbc.update(
        "update work_applied_effects set handler_code = ?, command_fingerprint = null where work_operation_id = ?",
        storedHandler,
        operationId);
    jdbc.update(
        "update work_types set name = 'later label', is_active = false where id = (select work_type_id from work_operations where id = ?)",
        operationId);
    for (long groupId : List.of(scenario.orchidGroupId(), resultId)) {
      var lineage = get("/api/orchid-groups/" + groupId + "/lineage");
      assertThat(lineage.status()).as(lineage.body().toString()).isEqualTo(200);
      assertThat(lineage.data().path("transformations")).hasSize(1);
      assertThat(lineage.data().path("transformations").get(0).path("relationType").asText())
          .isEqualTo(relation);
    }
    assertThat(get("/api/work-operations/" + operationId).status()).isEqualTo(200);
    var beforeReplay = snapshot();
    assertThat(execute(operationId).status()).isEqualTo(201);
    assertThat(snapshot()).isEqualTo(beforeReplay);
    var voided =
        post(
            "/api/work-operations/" + operationId + "/void",
            "{\"idempotencyKey\":\"contract-void\",\"reason\":\"contract regression\"}");
    assertThat(voided.status()).as(voided.body().toString()).isEqualTo(200);
    assertThat(voided.data().path("status").asText()).isEqualTo("VOIDED");
    var afterVoid = snapshot();
    var voidReplay =
        post(
            "/api/work-operations/" + operationId + "/void",
            "{\"idempotencyKey\":\"contract-void\",\"reason\":\"contract regression\"}");
    assertThat(voidReplay.status()).isEqualTo(200);
    assertThat(execute(operationId).status()).isEqualTo(201);
    assertThat(snapshot()).isEqualTo(afterVoid);
    var lineage = get("/api/orchid-groups/" + scenario.orchidGroupId() + "/lineage");
    assertThat(lineage.status()).isEqualTo(200);
    assertThat(lineage.data().path("transformations").get(0).path("relationType").asText())
        .isEqualTo(relation);
  }

  @ParameterizedTest
  @ValueSource(strings = {"UNKNOWN", "MERGE"})
  void unclassifiedOrWrongStructureResultRollsBackRealMutationAndCanRetry(String handler)
      throws Exception {
    long operationId = plan("REPOT");
    var before = snapshot();
    doAnswer(
            invocation -> {
              var applied = (WorkExecutionResult) invocation.callRealMethod();
              return new WorkExecutionResult(
                  handler,
                  applied.details(),
                  applied.resultOrchidGroupIds(),
                  applied.mutationLink());
            })
        .when(executor)
        .execute(any(), any(), anySet());
    try {
      assertThat(execute(operationId).status()).isEqualTo(500);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      reset(executor);
    }
    assertThat(execute(operationId).status()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from work_applied_effects where work_operation_id = ?",
                Integer.class,
                operationId))
        .isEqualTo(1);
  }

  private long plan(String code) throws Exception {
    var template = code.equals("MOVEMENT") ? WorkTypeTemplate.MOVEMENT : WorkTypeTemplate.REPOT;
    var type =
        types
            .findByCode(code)
            .orElseGet(
                () ->
                    types.saveAndFlush(
                        new WorkType(code, "contract type", template, true, true, true, 1)));
    var planned =
        post(
            "/api/work-operations",
            objectMapper.writeValueAsString(
                Map.of(
                    "workTypeId",
                    type.getId(),
                    "title",
                    "contract plan",
                    "plannedStartDate",
                    "2026-09-08",
                    "sourceScopeType",
                    "MANUAL_SELECTION",
                    "sourceOrchidGroupIds",
                    List.of(scenario.orchidGroupId()))));
    assertThat(planned.status()).as(planned.body().toString()).isEqualTo(201);
    long id = planned.data().path("id").asLong();
    assertThat(post("/api/work-operations/" + id + "/start", "").status()).isEqualTo(200);
    return id;
  }

  private ApiResult execute(long id) throws Exception {
    return post(
        "/api/work-operations/" + id + "/structure-change-executions",
        objectMapper.writeValueAsString(
            Map.of(
                "idempotencyKey",
                "contract-execution",
                "completedDate",
                "2026-09-08",
                "worker",
                "contract worker",
                "sources",
                List.of(
                    Map.of("sourceOrchidGroupId", scenario.orchidGroupId(), "inputQuantity", 100)),
                "results",
                List.of(
                    Map.of(
                        "bedZoneId",
                        scenario.bedZoneId(),
                        "quantity",
                        100,
                        "sourceOrchidGroupIds",
                        List.of(scenario.orchidGroupId()),
                        "attributeSourceOrchidGroupId",
                        scenario.orchidGroupId(),
                        "potSize",
                        "4치",
                        "ageYear",
                        3,
                        "purpose",
                        "NORMAL",
                        "startPosition",
                        6,
                        "endPosition",
                        8)))));
  }

  private Map<String, List<String>> snapshot() {
    var snapshot = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "orchid_group_lineage",
            "work_operations",
            "work_operation_targets",
            "work_target_executions",
            "work_applied_effects",
            "work_effect_orchid_groups",
            "work_command_receipts",
            "work_command_receipt_memberships",
            "audit_events")) {
      snapshot.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    }
    return snapshot;
  }
}
