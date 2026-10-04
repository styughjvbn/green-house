package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnifiedMovementRecordPostgresE2ETest extends WorkUndoSafetyTestBase {

  @Test
  void historicalDirectMoveJsonRemainsReadableAndCancellable() throws Exception {
    var created =
        post("/api/work-operations/structure-change-records/batch", request("historical", 12, 14));
    assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
    long work = created.data().get(0).path("id").asLong();
    long group = source();
    long zone =
        jdbc.queryForObject("SELECT bed_zone_id FROM orchid_groups WHERE id=?", Long.class, group);
    String legacy =
        """
				{"orchidGroupId":%d,"fromBedZoneId":%d,"toBedZoneId":%d,"startPosition":12,"endPosition":14}
				"""
            .formatted(group, zone, zone);
    jdbc.update(
        "UPDATE work_applied_effects SET handler_code='MOVE', effect_kind='ATTRIBUTE_CHANGE', result_details=CAST(? AS jsonb), command_details=CAST(? AS jsonb) WHERE work_operation_id=?",
        legacy,
        legacy,
        work);
    var details = get("/api/work-operations/" + work + "/details");
    assertThat(details.status()).as(details.body().toString()).isEqualTo(200);
    assertThat(details.data().path("executions").get(0).path("resultType").asText())
        .isEqualTo("MOVE");
    assertThat(
            details
                .data()
                .path("executions")
                .get(0)
                .path("results")
                .get(0)
                .path("bedZoneId")
                .asLong())
        .isEqualTo(zone);
    assertThat(
            post(
                    "/api/work-operations/" + work + "/cancel",
                    "{\"idempotencyKey\":\"undo-historical\",\"reason\":\"오등록\"}")
                .status())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT start_position FROM orchid_groups WHERE id=?", BigDecimal.class, group))
        .isEqualByComparingTo("6");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void singleFullMovementKeepsIdentityAndReplaysWithoutDuplicateWorkAndCanBeUndone()
      throws Exception {
    long source = source();
    String request = request("unified", 12, 14);
    long workCount = count("work_operations"), mutationCount = count("orchid_group_mutations");
    var first = post("/api/work-operations/structure-change-records/batch", request);
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    long work = first.data().get(0).path("id").asLong();
    var expectedDetails =
        objectMapper.readTree(
            """
        {"executionKey":"unified","sourceInputQuantities":{"%d":60},
         "lossQuantity":0,"increaseQuantity":0,
         "results":[{"orchidGroupId":%d,"quantity":60,"purpose":"NORMAL"}],
         "sourceOrchidGroupId":%d,"inputQuantity":60,"remainingQuantity":60,
         "resultOrchidGroupIds":[%d],"identityPreserved":true}
        """
                .formatted(source, source, source, source));
    assertThat(
            objectMapper.readTree(
                jdbc.queryForObject(
                    "select result_details::text from work_applied_effects where work_operation_id = ?",
                    String.class,
                    work)))
        .isEqualTo(expectedDetails);
    assertThat(
            objectMapper.readTree(
                jdbc.queryForObject(
                    "select execution.result_details::text from work_target_executions execution join work_operation_targets target on target.id = execution.work_operation_target_id where target.work_operation_id = ?",
                    String.class,
                    work)))
        .isEqualTo(expectedDetails);
    var replay = post("/api/work-operations/structure-change-records/batch", request);
    assertThat(replay.status()).as(replay.body().toString()).isEqualTo(201);
    assertThat(replay.data().get(0).path("id").asLong()).isEqualTo(work);
    assertThat(count("work_operations")).isEqualTo(workCount + 1);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationCount + 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT mutation_type FROM orchid_group_mutations ORDER BY id DESC LIMIT 1",
                String.class))
        .isEqualTo("MOVE");
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, source))
        .isEqualTo(60);
    assertThat(count("orchid_groups")).isEqualTo(3);
    assertThat(
            post(
                    "/api/work-operations/" + work + "/cancel",
                    "{\"idempotencyKey\":\"undo-unified\",\"reason\":\"오등록\"}")
                .status())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT start_position FROM orchid_groups WHERE id=?", BigDecimal.class, source))
        .isEqualByComparingTo("6");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void invalidPlacementRollsBackTheWholeRecord() throws Exception {
    long workCount = count("work_operations"), mutationCount = count("orchid_group_mutations");
    var response =
        post("/api/work-operations/structure-change-records/batch", request("invalid", 14, 12));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
    assertThat(count("work_operations")).isEqualTo(workCount);
    assertThat(count("orchid_group_mutations")).isEqualTo(mutationCount);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private long source() {
    return jdbc.queryForObject("SELECT id FROM orchid_groups WHERE quantity=60", Long.class);
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private String request(String key, int start, int end) {
    long group = source();
    long zone =
        jdbc.queryForObject("SELECT bed_zone_id FROM orchid_groups WHERE id=?", Long.class, group);
    long type = jdbc.queryForObject("SELECT id FROM work_types WHERE code='MOVEMENT'", Long.class);
    return """
				{"records":[{"operation":{"workTypeId":%d,"title":"자리 이동","plannedStartDate":"2026-07-16",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]},
				 "execution":{"idempotencyKey":"%s","completedDate":"2026-07-16",
				 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":60}],
				 "results":[{"bedZoneId":%d,"quantity":60,"attributeSourceOrchidGroupId":%d,
				 "purpose":"NORMAL","startPosition":%d,"endPosition":%d}]}}]}
				"""
        .formatted(type, group, key, group, zone, group, start, end);
  }
}
