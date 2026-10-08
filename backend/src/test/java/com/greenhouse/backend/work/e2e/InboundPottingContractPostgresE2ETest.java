package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.InboundPottingPlanService;
import com.greenhouse.backend.work.application.operation.WorkOperationProgressService;
import com.greenhouse.backend.work.application.operation.WorkRequestFingerprint;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class InboundPottingContractPostgresE2ETest extends WorkE2ETestBase {

  private static final LocalDate DATE = LocalDate.of(2026, 7, 18);

  @Autowired WorkTestDataSeeder seeder;
  @Autowired JdbcTemplate jdbc;
  @Autowired InboundRecordService inbounds;
  @Autowired InboundPottingOperationService potting;
  @Autowired InboundPottingPlanService plans;
  @Autowired WorkOperationProgressService progress;
  @Autowired OrchidGroupRepository groups;
  @Autowired OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired OrchidGroupLedgerReconciliationService reconciliation;

  private long inboundId;
  private long zoneId;

  @BeforeEach
  void prepare() {
    seeder.resetKeepingSequences();
    var scenario = seeder.seedContractScenario();
    zoneId = scenario.bedZoneId();
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
    long variety =
        jdbc.queryForObject(
            "select variety_id from orchid_groups where id = ?",
            Long.class,
            scenario.orchidGroupId());
    inboundId =
        inbounds
            .create(
                new InboundRecordCreateCommand(
                    DATE,
                    InboundType.FLASK_SEEDLING,
                    variety,
                    null,
                    31,
                    "배양실",
                    DATE,
                    null,
                    "입고 담당",
                    null))
            .id();
  }

  @Test
  void typedExecutionPreservesAllFieldsJsonFingerprintAndResultOrder() throws Exception {
    var operation = potting.executeNow(command(false));
    var expected = historicalDetails(false);
    var stored = storedDetails(operation.id());
    assertThat(objectMapper.readTree(stored))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(expected)));
    assertThat(new WorkRequestFingerprint().calculate(readDetails(stored)))
        .isEqualTo(new WorkRequestFingerprint().calculate(expected));
    var targetId = operation.targets().getFirst().id();
    assertThat(
            jdbc.queryForObject(
                "select command_fingerprint from work_applied_effects where work_operation_id = ?",
                String.class,
                operation.id()))
        .isEqualTo(
            new WorkRequestFingerprint()
                .calculate(
                    Map.of(
                        "handlerCode",
                        "POTTING",
                        "targetId",
                        targetId,
                        "businessDate",
                        DATE,
                        "worker",
                        "포트 담당",
                        "details",
                        expected)));
    assertResults(operation.id());
    var beforeReplay = snapshot();
    assertThat(potting.executeNow(command(false)).id()).isEqualTo(operation.id());
    assertThat(snapshot()).isEqualTo(beforeReplay);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void existingEffectWithoutReceiptAcceptsReplayAndRejectsChangedContent(boolean legacyKey)
      throws Exception {
    var operation = potting.executeNow(command(false));
    jdbc.update(
        "delete from work_command_receipts where receipt_key = ?",
        "POTTING:" + inboundId + ":typed-contract");
    if (legacyKey) {
      jdbc.update(
          "update work_applied_effects set effect_key = 'POTTING:typed-contract', command_fingerprint = null where work_operation_id = ?",
          operation.id());
    }
    var beforeReplay = snapshot();
    assertThat(potting.executeNow(command(false)).id()).isEqualTo(operation.id());
    // The missing receipt is recreated; the original effect, mutation and groups stay intact.
    var afterReplay = snapshot();
    beforeReplay.remove("work_command_receipts");
    afterReplay.remove("work_command_receipts");
    assertThat(afterReplay).isEqualTo(beforeReplay);
    var original = command(false);
    var changed =
        new InboundPottingCommand(
            original.idempotencyKey(),
            inboundId,
            DATE,
            original.results(),
            original.worker(),
            "다른 메모");
    jdbc.update(
        "delete from work_command_receipts where receipt_key = ?",
        "POTTING:" + inboundId + ":typed-contract");
    var beforeConflict = snapshot();
    assertThatThrownBy(() -> potting.executeNow(changed))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("멱등 키");
    assertThat(snapshot()).isEqualTo(beforeConflict);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void genericTargetCompletionRetainsHistoricalJsonAndReplayWithOrWithoutStoredFingerprint(
      boolean missingFingerprint) throws Exception {
    var plan = plan();
    var details = historicalDetails(false);
    details.put("pottingDate", "2026-07-18");
    var request = new WorkTargetExecutionRequest("포트 담당", details, DATE);
    progress.completeTarget(plan.operationId(), plan.targetId(), request);
    assertThat(objectMapper.readTree(storedDetails(plan.operationId())))
        .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(details)));
    assertResults(plan.operationId());
    if (missingFingerprint) {
      jdbc.update(
          "update work_applied_effects set command_fingerprint = null where work_operation_id = ?",
          plan.operationId());
    }
    var beforeReplay = snapshot();
    progress.completeTarget(
        plan.operationId(),
        plan.targetId(),
        new WorkTargetExecutionRequest("포트 담당", details, null));
    assertThat(snapshot()).isEqualTo(beforeReplay);
    var changed = new LinkedHashMap<>(details);
    changed.put("memo", "다른 메모");
    assertThatThrownBy(
            () ->
                progress.completeTarget(
                    plan.operationId(),
                    plan.targetId(),
                    new WorkTargetExecutionRequest("포트 담당", changed, DATE)))
        .isInstanceOf(ConflictException.class);
    assertThat(snapshot()).isEqualTo(beforeReplay);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void latePlacementFailureRollsBackInboundWorkEffectsMutationAndAudit(boolean genericTarget)
      throws Exception {
    Plan plan = genericTarget ? plan() : null;
    var before = snapshot();
    assertThatThrownBy(
            () -> {
              if (genericTarget) {
                progress.completeTarget(
                    plan.operationId(),
                    plan.targetId(),
                    new WorkTargetExecutionRequest("포트 담당", historicalDetails(true), DATE));
              } else {
                potting.executeNow(command(true));
              }
            })
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(snapshot()).isEqualTo(before);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void effectInsertFailureRollsBackFarmMutationAndAllowsTheSameRequestToRetry(boolean genericTarget)
      throws Exception {
    Plan plan = genericTarget ? plan() : null;
    var before = snapshot();
    jdbc.execute(
        "alter table work_applied_effects add constraint test_reject_potting_effect check (handler_code <> 'POTTING')");
    try {
      assertThatThrownBy(
              () -> {
                if (genericTarget) {
                  progress.completeTarget(
                      plan.operationId(),
                      plan.targetId(),
                      new WorkTargetExecutionRequest("포트 담당", historicalDetails(false), DATE));
                } else {
                  potting.executeNow(command(false));
                }
              })
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute("alter table work_applied_effects drop constraint test_reject_potting_effect");
    }
    long operationId =
        genericTarget
            ? progress
                .completeTarget(
                    plan.operationId(),
                    plan.targetId(),
                    new WorkTargetExecutionRequest("포트 담당", historicalDetails(false), DATE))
                .id()
            : potting.executeNow(command(false)).id();
    assertResults(operationId);
  }

  private Plan plan() {
    var operation =
        plans.create(
            new InboundPottingPlanCreateRequest(
                "포트 계약", DATE, DATE, List.of(inboundId), "포트 담당", null));
    progress.start(operation.id());
    return new Plan(operation.id(), operation.targets().getFirst().id());
  }

  private InboundPottingCommand command(boolean overlap) {
    return new InboundPottingCommand(
        "typed-contract",
        inboundId,
        DATE,
        List.of(
            new InboundPottingResultInput(
                zoneId,
                13,
                "2인치",
                2,
                "트레이",
                3,
                true,
                new BigDecimal("12.25"),
                new BigDecimal("14.75"),
                "결과 메모"),
            new InboundPottingResultInput(
                zoneId,
                17,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal(overlap ? "13.25" : "15.25"),
                new BigDecimal("17.75"),
                null)),
        "포트 담당",
        "포트 완료");
  }

  private Map<String, Object> historicalDetails(boolean overlap) throws Exception {
    try (var input =
        getClass().getResourceAsStream("/work/compatibility/potting-command-rich.json")) {
      var details = objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
      var results = (List<?>) details.get("results");
      for (Object row : results) {
        @SuppressWarnings("unchecked")
        var result = (Map<String, Object>) row;
        result.put("bedZoneId", zoneId);
      }
      if (overlap) {
        @SuppressWarnings("unchecked")
        var second = (Map<String, Object>) results.get(1);
        second.put("startPosition", new BigDecimal("13.25"));
      }
      return details;
    }
  }

  private String storedDetails(long operationId) {
    return jdbc.queryForObject(
        "select command_details::text from work_applied_effects where work_operation_id = ?",
        String.class,
        operationId);
  }

  private Map<String, Object> readDetails(String json) throws Exception {
    return objectMapper.readValue(json, new TypeReference<>() {});
  }

  private void assertResults(long operationId) throws Exception {
    var effect =
        jdbc.queryForMap(
            "select result_details::text, mutation_id, correlation_id from work_applied_effects where work_operation_id = ?",
            operationId);
    var ids =
        objectMapper.readTree((String) effect.get("result_details")).path("createdOrchidGroupIds");
    assertThat(ids.size()).isEqualTo(2);
    var expectedDetails = objectMapper.createObjectNode();
    expectedDetails.put("inboundRecordId", inboundId);
    expectedDetails.set("createdOrchidGroupIds", ids);
    expectedDetails.put("actualQuantity", 30);
    expectedDetails.put("resultCount", 2);
    assertThat(objectMapper.readTree((String) effect.get("result_details")))
        .isEqualTo(objectMapper.readTree(expectedDetails.toString()));
    assertThat(
            objectMapper.readTree(
                jdbc.queryForObject(
                    "select execution.result_details::text from work_target_executions execution join work_operation_targets target on target.id = execution.work_operation_target_id where target.work_operation_id = ?",
                    String.class,
                    operationId)))
        .isEqualTo(objectMapper.readTree(expectedDetails.toString()));

    var rows =
        jdbc.queryForList(
            "select id, quantity, age_year, tray_count, split_placement_allowed, start_position, end_position, memo from orchid_groups where inbound_record_id = ? order by id",
            inboundId);
    assertThat(rows).hasSize(2);
    assertThat(rows.getFirst())
        .containsEntry("id", ids.get(0).asLong())
        .containsEntry("quantity", 13)
        .containsEntry("age_year", 2)
        .containsEntry("tray_count", 3)
        .containsEntry("split_placement_allowed", true)
        .containsEntry("start_position", new BigDecimal("12.25"))
        .containsEntry("end_position", new BigDecimal("14.75"))
        .containsEntry("memo", "결과 메모");
    assertThat(rows.get(1)).containsEntry("id", ids.get(1).asLong()).containsEntry("quantity", 17);
    assertThat(
            jdbc.queryForObject(
                "select status from inbound_records where id = ?", String.class, inboundId))
        .isEqualTo("PLACED");
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, operationId))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "select correlation_id from orchid_group_mutations where id = ?",
                UUID.class,
                effect.get("mutation_id")))
        .isEqualTo(effect.get("correlation_id"));
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Map<String, String> snapshot() {
    var result = new LinkedHashMap<String, String>();
    var tables =
        jdbc.queryForList(
            "select tablename from pg_tables where schemaname = 'public' order by tablename",
            String.class);
    for (String table : tables) {
      String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
      result.put(
          table,
          jdbc.queryForObject(
              "select coalesce(jsonb_agg(row_value order by row_value::text), '[]'::jsonb)::text from (select to_jsonb(t) as row_value from "
                  + quoted
                  + " t) rows",
              String.class));
    }
    return result;
  }

  private record Plan(long operationId, long targetId) {}
}
