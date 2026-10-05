package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.collection.OrchidGroupCollectionService;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionCreateRequest;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionMemberAddRequest;
import com.greenhouse.backend.farm.dto.collection.OrchidGroupCollectionMemberResponse;
import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.InboundPottingPlanService;
import com.greenhouse.backend.work.application.operation.InboundPottingVoidPort;
import com.greenhouse.backend.work.dto.effect.InboundPottingPlanCreateRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;

class WorkUndoInboundPostgresE2ETest extends WorkUndoSafetyTestBase {

  @Autowired InboundRecordService inbounds;

  @MockitoSpyBean InboundPottingOperationService potting;

  @Autowired InboundPottingPlanService plans;

  @Autowired InboundPottingVoidPort voidPort;

  @Autowired OrchidGroupCommandService groupCommands;

  @Autowired OrchidGroupCollectionService collections;

  private final LocalDate date = LocalDate.of(2026, 8, 20);

  private long inbound() {
    long variety = jdbc.queryForObject("select min(variety_id) from orchid_groups", Long.class);
    return inbounds
        .create(
            new InboundRecordCreateCommand(
                date,
                InboundType.FLASK_SEEDLING,
                variety,
                null,
                20,
                "배양실",
                date,
                null,
                "audit",
                null))
        .id();
  }

  private long execute(long inbound, String key) {
    return executeAt(inbound, key, 12, 14);
  }

  private long executeAt(long inbound, String key, int start, int end) {
    long zone = jdbc.queryForObject("select min(bed_zone_id) from orchid_groups", Long.class);
    return potting
        .executeNow(
            new InboundPottingCommand(
                key,
                inbound,
                date,
                List.of(
                    new InboundPottingResultInput(
                        zone,
                        20,
                        "2인치",
                        1,
                        "트레이",
                        1,
                        false,
                        BigDecimal.valueOf(start),
                        BigDecimal.valueOf(end),
                        null)),
                "audit",
                null))
        .id();
  }

  private ApiResult voidPotting(long inbound) throws Exception {
    return post(
        "/api/inbound-records/" + inbound + "/potting-void",
        "{\"idempotencyKey\":\"original-undo\",\"reason\":\"audit\"}");
  }

  @Test
  void inboundPottingVoidBusinessApiAndFarmPortRequireTheCallerTransaction() {
    long inbound = inbound();
    execute(inbound, "mandatory-potting");
    var before = pottingState();
    assertThat(
            Assertions.catchThrowable(
                () -> potting.voidForInbound(inbound, "mandatory-void", "audit")))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(
            Assertions.catchThrowable(
                () -> voidPort.voidPotting(inbound, "mandatory-void", "audit")))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(pottingState()).isEqualTo(before);
  }

  @Test
  void legacyPottingVoidReceiptKeepsItsFingerprintAndNeverCreatesMembership() throws Exception {
    long inbound = inbound();
    long original = execute(inbound, "legacy-potting");
    var memberships =
        jdbc.queryForList(
            "SELECT * FROM work_command_receipt_memberships ORDER BY receipt_key, operation_id");
    String receiptKey = "INBOUND_POTTING_VOID:" + inbound + ":original-undo";
    String legacyFingerprint =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        ("{\"inboundRecordId\":" + inbound + ",\"reason\":\"audit\"}")
                            .getBytes(StandardCharsets.UTF_8)));
    var first =
        post(
            "/api/inbound-records/" + inbound + "/potting-void",
            "{\"idempotencyKey\":\" original-undo \",\"reason\":\" audit \"}");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT request_fingerprint FROM work_command_receipts WHERE receipt_key = ?",
                String.class,
                receiptKey))
        .isEqualTo(legacyFingerprint);
    jdbc.update("DELETE FROM work_command_receipts WHERE receipt_key = ?", receiptKey);
    // Reinstall the old completed identity directly; current entities cannot reconstruct it.
    jdbc.update(
        "INSERT INTO work_command_receipts (receipt_key, request_fingerprint, result_operation_ids, created_at) VALUES (?, ?, CAST(? AS jsonb), CURRENT_TIMESTAMP)",
        receiptKey,
        legacyFingerprint,
        "[" + original + "]");
    long replacement = execute(inbound, "legacy-replacement");
    var beforeReplay = pottingState();
    assertThat(voidPotting(inbound).status()).isEqualTo(200);
    assertThat(pottingState()).isEqualTo(beforeReplay);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM work_operations WHERE id = ?", String.class, replacement))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForList(
                "SELECT * FROM work_command_receipt_memberships ORDER BY receipt_key, operation_id"))
        .isEqualTo(memberships);
    var changed =
        post(
            "/api/inbound-records/" + inbound + "/potting-void",
            "{\"idempotencyKey\":\"original-undo\",\"reason\":\"different\"}");
    assertThat(changed.status()).isEqualTo(409);
    assertThat(changed.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(pottingState()).isEqualTo(beforeReplay);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void inboundAuditFailureRollsBackPottingVoidAndAllowsTheSameKeyRetry() throws Exception {
    long inbound = inbound();
    execute(inbound, "rollback-potting");
    var before = pottingState();
    jdbc.execute(
        "ALTER TABLE audit_events ADD CONSTRAINT test_potting_void_audit CHECK (source <> 'INBOUND_MANAGEMENT' OR entity_type <> 'INBOUND_RECORD' OR entity_id <> "
            + inbound
            + " OR action <> 'UPDATED')");
    ApiResult failed;
    try {
      failed = voidPotting(inbound);
    } finally {
      jdbc.execute("ALTER TABLE audit_events DROP CONSTRAINT test_potting_void_audit");
    }
    assertThat(failed.status()).as(failed.body().toString()).isBetween(400, 599);
    assertThat(pottingState()).isEqualTo(before);
    var retry = voidPotting(inbound);
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM inbound_records WHERE id = ?", String.class, inbound))
        .isEqualTo("POTTING_PENDING");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type = 'COMPENSATION'",
                Long.class))
        .isEqualTo(1);
    var after = pottingState();
    assertThat(voidPotting(inbound).status()).isEqualTo(200);
    assertThat(pottingState()).isEqualTo(after);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  private Map<String, Object> pottingState() {
    var state = new LinkedHashMap<String, Object>();
    for (String table :
        List.of(
            "inbound_records",
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
      state.put(
          table,
          jdbc.queryForList(
              "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM "
                  + table
                  + " ORDER BY id) snapshot",
              String.class));
    }
    state.put(
        "receipts",
        jdbc.queryForList(
            "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM work_command_receipts ORDER BY receipt_key) snapshot",
            String.class));
    state.put(
        "memberships",
        jdbc.queryForList(
            "SELECT row_to_json(snapshot)::text FROM (SELECT * FROM work_command_receipt_memberships ORDER BY receipt_key, operation_id) snapshot",
            String.class));
    return state;
  }

  @Test
  void concurrentPottingUndoCreatesOneCompensationAndRejectsChangedReason() throws Exception {
    long inbound = inbound();
    execute(inbound, "concurrent-potting");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> voidPotting(inbound));
      var second = executor.submit(() -> voidPotting(inbound));
      assertThat(first.get(20, TimeUnit.SECONDS).status()).isEqualTo(200);
      assertThat(second.get(20, TimeUnit.SECONDS).status()).isEqualTo(200);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from orchid_group_mutations where mutation_type = 'COMPENSATION'",
                Long.class))
        .isEqualTo(1);
    var changed =
        post(
            "/api/inbound-records/" + inbound + "/potting-void",
            "{\"idempotencyKey\":\"original-undo\",\"reason\":\"different\"}");
    assertThat(changed.status()).as(changed.body().toString()).isEqualTo(409);
    assertThat(changed.body().path("error").path("code").asText())
        .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void siblingPottingExecutionsUseTheSameInboundLockOrder(boolean siblingFirst) throws Exception {
    long first = inbound(), sibling = inbound();
    var plan =
        plans.create(
            new InboundPottingPlanCreateRequest(
                "병렬 포트", date, date, List.of(first, sibling), "audit", null));
    var firstWorker = new PostgresLockTestSupport.Worker();
    var secondWorker = new PostgresLockTestSupport.Worker();
    doAnswer(
            invocation -> {
              InboundPottingCommand command = invocation.getArgument(0);
              (command.inboundRecordId().equals(first) ? firstWorker : secondWorker).capture(jdbc);
              return invocation.callRealMethod();
            })
        .when(potting)
        .executeNow(any());
    try (var connection = dataSource.getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      int owner = PostgresLockTestSupport.backendPid(connection);
      try (var statement =
          connection.prepareStatement("select id from inbound_records where id = ? for update")) {
        statement.setLong(1, first);
        try (var rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
        }
      }
      try {
        var one =
            executor.submit(
                () ->
                    siblingFirst
                        ? executeAt(sibling, "sibling-two", 15, 17)
                        : executeAt(first, "sibling-one", 12, 14));
        (siblingFirst ? secondWorker : firstWorker).awaitBlockedBy(jdbc, owner, one);
        var two =
            executor.submit(
                () ->
                    siblingFirst
                        ? executeAt(first, "sibling-one", 12, 14)
                        : executeAt(sibling, "sibling-two", 15, 17));
        (siblingFirst ? firstWorker : secondWorker).awaitBlockedBy(jdbc, owner, two);
        connection.commit();
        assertThat(one.get(20, TimeUnit.SECONDS)).isEqualTo(plan.id());
        assertThat(two.get(20, TimeUnit.SECONDS)).isEqualTo(plan.id());
      } finally {
        connection.rollback();
      }
    }
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, plan.id()))
        .isEqualTo("COMPLETED");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void pottingUndoRetriesDoNotUndoAReplacementPottingWork() throws Exception {
    long inbound = inbound();
    long first = execute(inbound, "first-potting");
    var undo = voidPotting(inbound);
    assertThat(undo.status()).as(undo.body().toString()).isEqualTo(200);
    var immediateRetry = voidPotting(inbound);
    assertThat(immediateRetry.status()).as(immediateRetry.body().toString()).isEqualTo(200);
    long second = execute(inbound, "second-potting");
    assertThat(second).isNotEqualTo(first);
    var delayedRetry = voidPotting(inbound);
    assertThat(delayedRetry.status()).as(delayedRetry.body().toString()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, second))
        .isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "select status from inbound_records where id = ?", String.class, inbound))
        .isEqualTo("PLACED");
    assertThat(
            jdbc.queryForObject(
                "select sum(quantity) from orchid_groups where inbound_record_id = ? and quantity > 0",
                Long.class,
                inbound))
        .isEqualTo(20L);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void inboundActionsCanCancelPartiallyExecutedPotting() throws Exception {
    long first = inbound(), sibling = inbound();
    var plan =
        plans.create(
            new InboundPottingPlanCreateRequest(
                "부분 포트", date, date, List.of(first, sibling), "audit", null));
    long operation = execute(first, "partial-potting");
    assertThat(operation).isEqualTo(plan.id());
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, operation))
        .isEqualTo("IN_PROGRESS");
    var detail = get("/api/inbound-records/" + first);
    assertThat(detail.status()).as(detail.body().toString()).isEqualTo(200);
    assertThat(detail.data().path("availableActions").toString())
        .contains("VOID_POTTING", "CANCEL");
    var undo = voidPotting(first);
    var cancel =
        post(
            "/api/inbound-records/" + first + "/cancel",
            "{\"idempotencyKey\":\"partial-cancel\",\"memo\":\"audit\"}");
    assertThat(undo.status()).as(undo.body().toString()).isEqualTo(200);
    assertThat(cancel.status()).as(cancel.body().toString()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, operation))
        .isEqualTo("VOIDED");
    assertThat(
            jdbc.queryForObject(
                "select status from inbound_records where id = ?", String.class, sibling))
        .isEqualTo("POTTING_PENDING");
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void ordinaryGroupPatchCannotAssignCreationCanceled() throws Exception {
    long group =
        jdbc.queryForObject("select min(id) from orchid_groups where quantity = 60", Long.class);
    long variety =
        jdbc.queryForObject("select variety_id from orchid_groups where id = ?", Long.class, group);
    assertThat(Assertions.catchThrowable(() -> groupCommands.delete(group)))
        .isInstanceOf(ConflictException.class);
    var response =
        patchJson(
            "/api/orchid-groups/" + group,
            "{\"varietyId\":"
                + variety
                + ",\"quantity\":60,\"potSize\":\"4치\",\"ageYear\":3,\"status\":\"생성 취소\",\"startPosition\":6,\"endPosition\":8}");
    assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "select quantity from orchid_groups where id = ?", Integer.class, group))
        .isEqualTo(60);
    assertThat(
            jdbc.queryForObject(
                "select status from orchid_groups where id = ?", String.class, group))
        .isEqualTo("정상");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from orchid_group_mutations where mutation_type = 'CANCEL_CREATION'",
                Long.class))
        .isZero();
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }

  @Test
  void groupMembershipIsPreservedAcrossAllUndoPaths() throws Exception {
    long original = jdbc.queryForObject("select min(id) from work_operations", Long.class);
    long structureGroup =
        jdbc.queryForObject("select min(id) from orchid_groups where quantity = 60", Long.class);
    long inbound = inbound();
    execute(inbound, "collection-potting");
    long pottingGroup =
        jdbc.queryForObject(
            "select max(id) from orchid_groups where inbound_record_id = ?", Long.class, inbound);
    var collection =
        collections.create(new OrchidGroupCollectionCreateRequest("소속 보존", null, null, "audit"));
    collections.addMembers(
        collection.id(),
        new OrchidGroupCollectionMemberAddRequest(Set.of(structureGroup, pottingGroup), "audit"));
    assertThat(
            post(
                    "/api/work-operations/" + original + "/cancel",
                    "{\"idempotencyKey\":\"collection-structure-undo\",\"reason\":\"audit\"}")
                .status())
        .isEqualTo(200);
    assertThat(voidPotting(inbound).status()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select removed_at is not null from orchid_group_collection_members where collection_id = ? and orchid_group_id = ?",
                Boolean.class,
                collection.id(),
                structureGroup))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "select removed_at is not null from orchid_group_collection_members where collection_id = ? and orchid_group_id = ?",
                Boolean.class,
                collection.id(),
                pottingGroup))
        .isFalse();
    assertThat(collections.get(collection.id()).members())
        .extracting(OrchidGroupCollectionMemberResponse::orchidGroupId)
        .containsExactlyInAnyOrder(structureGroup, pottingGroup);
    assertThat(reconciliation.reconcile().ready()).isTrue();
  }
}
