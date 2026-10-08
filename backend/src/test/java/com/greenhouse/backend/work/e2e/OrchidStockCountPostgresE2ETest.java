package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupQuantityMutationItem;
import com.greenhouse.backend.farm.api.orchid.ReserveOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.mutation.engine.OrchidGroupMutationEngine;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.test.context.TestPropertySource(
    properties = {
      "features.stock-count.enabled=true",
      "features.work-quantity-correction.enabled=true"
    })
class OrchidStockCountPostgresE2ETest extends WorkUndoSafetyTestBase {

  @Autowired PlatformTransactionManager transactions;

  @Autowired OrchidGroupMutationEngine engine;

  @Test
  void concurrentDuplicatesHaveOneAuditAndOneMutation() throws Exception {
    long group = result();
    String request = request("parallel", group, 70, null);
    assertThat(parallel(group, request, request)).extracting(ApiResult::status).containsOnly(201);
    assertThat(count("orchid_stock_counts")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE source_type='STOCK_COUNT'",
                Long.class))
        .isEqualTo(1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void concurrentDifferentCountsRequireReconfirmationForTheLoser() throws Exception {
    long group = result();
    var responses =
        parallel(group, request("first", group, 70, null), request("second", group, 80, null));
    assertThat(responses).extracting(ApiResult::status).containsExactlyInAnyOrder(201, 409);
    assertThat(
            responses.stream()
                .filter(r -> r.status() == 409)
                .findFirst()
                .orElseThrow()
                .body()
                .toString())
        .contains("STOCK_COUNT_STALE");
    assertThat(count("orchid_stock_counts")).isEqualTo(1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void identicalQuantityDoesNotCreateAnAudit() throws Exception {
    long group = result();
    assertThat(post(path(group), request("no-change", group, 60, null)).status()).isEqualTo(400);
    assertThat(count("orchid_stock_counts")).isZero();
  }

  @Test
  void reservationsRejectCountAndRollBackBothReceiptAndLedger() throws Exception {
    long group = result();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status ->
                engine.reserve(
                    new ReserveOrchidGroupsMutationCommand(
                        OrchidGroupMutationSources.farmRequest("TEST", "reservation", "RESERVE"),
                        List.of(new OrchidGroupQuantityMutationItem(group, 50)),
                        LocalDate.of(2026, 7, 15),
                        "예약 검증")));
    var response = post(path(group), request("reserved", group, 40, null));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
    assertThat(count("orchid_stock_counts")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE source_type='STOCK_COUNT'",
                Long.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, group))
        .isEqualTo(60);
  }

  @Test
  void countBlocksUndoRatherThanOverwritingCurrentQuantity() throws Exception {
    long group = result();
    assertThat(post(path(group), request("count-undo", group, 65, null)).status()).isEqualTo(201);
    long work = jdbc.queryForObject("SELECT min(id) FROM work_operations", Long.class);
    var response =
        post(
            "/api/work-operations/" + work + "/cancel",
            "{\"idempotencyKey\":\"undo\",\"reason\":\"잘못 등록\"}");
    assertThat(response.status()).isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, group))
        .isEqualTo(65);
  }

  @Test
  void countsChangeOnlyCurrentQuantityAndDoNotCreateOrRewriteWork() throws Exception {
    long group = result();
    long workCount = count("work_operations");
    String original =
        jdbc.queryForObject(
            "SELECT result_details::text FROM work_applied_effects ORDER BY id LIMIT 1",
            String.class);
    String request = request("count", group, 110, null);
    var response = post(path(group), request);
    assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
    assertThat(response.data().path("difference").asInt()).isEqualTo(50);
    assertThat(post(path(group), request).status()).isEqualTo(201);
    assertThat(count("orchid_stock_counts")).isEqualTo(1);
    assertThat(count("work_operations")).isEqualTo(workCount);
    assertThat(
            jdbc.queryForObject(
                "SELECT result_details::text FROM work_applied_effects ORDER BY id LIMIT 1",
                String.class))
        .isEqualTo(original);
    assertThat(get(path(group)).data().path("totalElements").asLong()).isEqualTo(1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void subsequentMovementUsesCountedQuantityAndCanBeCorrected() throws Exception {
    long zone =
        jdbc.queryForObject(
            "SELECT bed_zone_id FROM orchid_groups WHERE id=?", Long.class, result());
    long variety =
        jdbc.queryForObject(
            "SELECT variety_id FROM orchid_groups WHERE id=?", Long.class, result());
    var created =
        post(
            "/api/orchid-groups",
            """
						{"bedZoneId":%d,"varietyId":%d,"quantity":60,"potSize":"4치","ageYear":3,"status":"정상","startPosition":12,"endPosition":13}
						"""
                .formatted(zone, variety));
    assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
    long group = created.data().path("id").asLong();
    assertThat(post(path(group), request("before-move", group, 65, null)).status()).isEqualTo(201);
    long type = jdbc.queryForObject("SELECT id FROM work_types WHERE code='MOVEMENT'", Long.class);
    var movement =
        post(
            "/api/work-operations/structure-change-records/batch",
            """
						{"records":[{"operation":{"workTypeId":%d,"title":"실사 후 이동","plannedStartDate":"2026-07-15",
						"sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]},
						"execution":{"idempotencyKey":"move-after-count","completedDate":"2026-07-15",
						"sources":[{"sourceOrchidGroupId":%d,"inputQuantity":65}],
						"results":[{"bedZoneId":%d,"quantity":65,"attributeSourceOrchidGroupId":%d,"purpose":"NORMAL","startPosition":14,"endPosition":16}]}}]}
						"""
                .formatted(type, group, group, zone, group));
    assertThat(movement.status()).as(movement.body().toString()).isEqualTo(201);
    long work = jdbc.queryForObject("SELECT max(id) FROM work_operations", Long.class);
    long effect = jdbc.queryForObject("SELECT max(id) FROM work_applied_effects", Long.class);
    var corrected =
        post(
            "/api/work-operations/" + work + "/corrections",
            """
						{"idempotencyKey":"move-record-correction","workDate":"2026-07-15","reason":"투입량 입력 오류",
						"orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":70,"status":"정상"}],
						"quantityCorrections":[{"executionId":%d,"sourceInputQuantities":{"%d":70},"lossQuantity":0,"increaseQuantity":0}]}
						"""
                .formatted(group, effect, group));
    assertThat(corrected.status()).as(corrected.body().toString()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id=?", Integer.class, group))
        .isEqualTo(70);
    assertThat(count("orchid_stock_counts")).isEqualTo(1);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void refusesStaleRevisionAndPastDateAndReservedQuantity() throws Exception {
    long group = result();
    var stale = post(path(group), request("stale", group, 55, -1L));
    assertThat(stale.status()).isEqualTo(400); // DTO prevents negative revisions.
    long rev =
        get("/api/orchid-groups/" + group + "/stock-count-context")
            .data()
            .path("stateRevision")
            .asLong();
    assertThat(post(path(group), request("count", group, 65, null)).status()).isEqualTo(201);
    var conflict = post(path(group), request("stale2", group, 55, rev));
    assertThat(conflict.status()).as(conflict.body().toString()).isEqualTo(409);
    assertThat(conflict.body().toString()).contains("STOCK_COUNT_STALE");
    var old =
        request("old", group, 55, null)
            .replace(
                get("/api/orchid-groups/" + group + "/stock-count-context")
                    .data()
                    .path("businessDate")
                    .asText(),
                "2026-01-01");
    assertThat(post(path(group), old).status()).isEqualTo(400);
    assertThat(count("orchid_stock_counts")).isEqualTo(1);
  }

  @Test
  void rejectsReusingAKeyWithChangedContentAndBlocksWorkCorrectionAfterCount() throws Exception {
    long group = result();
    String body = request("stable", group, 65, null);
    assertThat(post(path(group), body).status()).isEqualTo(201);
    assertThat(
            post(path(group), body.replace("\"actualQuantity\":65", "\"actualQuantity\":66"))
                .status())
        .isEqualTo(409);
    long work = jdbc.queryForObject("SELECT min(id) FROM work_operations", Long.class);
    var correction =
        post(
            "/api/work-operations/" + work + "/corrections",
            """
				{"idempotencyKey":"after-count","workDate":"2026-07-15","reason":"기록 정정",
				"orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":55,"status":"정상"}],
				"quantityCorrections":[{"executionId":%d,"lossQuantity":5,"increaseQuantity":0}]}
				"""
                .formatted(
                    group,
                    jdbc.queryForObject("SELECT min(id) FROM work_applied_effects", Long.class)));
    assertThat(correction.status()).as(correction.body().toString()).isEqualTo(409);
    assertThat(correction.body().toString()).contains("WORK_CORRECTION_AFTER_STOCK_COUNT");
  }

  @Test
  void cannotReactivateClosedSourceGroups() throws Exception {
    long source = jdbc.queryForObject("SELECT id FROM orchid_groups WHERE quantity=0", Long.class);
    assertThat(
            get("/api/orchid-groups/" + source + "/stock-count-context")
                .data()
                .path("adjustable")
                .asBoolean())
        .isFalse();
    var result = post(path(source), request("reactivate", source, 100, null));
    assertThat(result.status()).as(result.body().toString()).isEqualTo(400);
    assertThat(count("orchid_stock_counts")).isZero();
  }

  private long result() {
    return jdbc.queryForObject("SELECT id FROM orchid_groups WHERE quantity=60", Long.class);
  }

  private List<ApiResult> parallel(long group, String first, String second) throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(first, second).stream()
              .map(
                  request ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(5, TimeUnit.SECONDS))
                              throw new AssertionError("start timeout");
                            return post(path(group), request);
                          }))
              .toList();
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          futures.getFirst().get(20, TimeUnit.SECONDS),
          futures.getLast().get(20, TimeUnit.SECONDS));
    }
  }

  private long count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
  }

  private String path(long id) {
    return "/api/orchid-groups/" + id + "/stock-counts";
  }

  private String request(String key, long id, int quantity, Long revision) throws Exception {
    var context = get("/api/orchid-groups/" + id + "/stock-count-context").data();
    return """
				{"idempotencyKey":"%s","expectedRevision":%d,"countedDate":"%s","actualQuantity":%d,"reason":"실사 차이 원인 미확인","worker":"검수자"}
				"""
        .formatted(
            key,
            revision == null ? context.path("stateRevision").asLong() : revision,
            context.path("businessDate").asText(),
            quantity);
  }
}
