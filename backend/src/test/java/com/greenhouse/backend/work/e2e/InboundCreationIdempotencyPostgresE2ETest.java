package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.inbound.InboundPlacementInput;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordUpdateRequest;
import com.greenhouse.backend.farm.variety.application.InboundVarietyInput;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = "app.orchid-ledger.writer-version=1.1.0")
class InboundCreationIdempotencyPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
  @Autowired private WorkTestDataSeeder seeder;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;
  @Autowired private InboundRecordService inbounds;
  @Autowired private InboundPottingOperationService potting;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManagerFactory entityManagerFactory;
  private Long varietyId;
  private Long zoneId;
  private String newVarietyName;

  @BeforeEach
  void seed() {
    seeder.resetKeepingSequences();
    jdbc.execute("TRUNCATE inbound_creation_receipts CONTINUE IDENTITY");
    newVarietyName = "신규 입고 " + UUID.randomUUID();
    var scenario = seeder.seedContractScenario();
    zoneId = scenario.bedZoneId();
    varietyId =
        jdbc.queryForObject(
            "select variety_id from orchid_groups where id = ?",
            Long.class,
            scenario.orchidGroupId());
    var key = UUID.randomUUID();
    ledgerFixture.seedBaseline(key, DATE, "1.0.0");
    ledgerFixture.activate(key);
  }

  @Test
  void repeatedCreateReturnsFirstResponseAndOnlyOneCompletedInboundWork() throws Exception {
    var request = flask(10, null);
    var first =
        post(
            "/api/inbound-records",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "lost-response"));
    var retry =
        post(
            "/api/inbound-records",
            objectMapper.writeValueAsString(request),
            Map.of("Idempotency-Key", "lost-response"));
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(jdbc.queryForObject("select count(*) from inbound_records", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from work_operations where status = 'COMPLETED'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void changedCreateCannotReuseSuccessfulRequestKey() throws Exception {
    var first =
        post(
            "/api/inbound-records",
            objectMapper.writeValueAsString(flask(10, null)),
            Map.of("Idempotency-Key", "changed"));
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    var changed =
        post(
            "/api/inbound-records",
            objectMapper.writeValueAsString(flask(20, "변경")),
            Map.of("Idempotency-Key", "changed"));
    assertThat(changed.status()).isEqualTo(409);
    assertThat(changed.body().path("error").path("code").asText())
        .isEqualTo("INBOUND_CREATE_REQUEST_KEY_CONFLICT");
    assertThat(jdbc.queryForObject("select count(*) from inbound_records", Integer.class))
        .isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "FLASK_SEEDLING,false",
    "FLASK_SEEDLING,true",
    "POTTED_SEEDLING,false",
    "POTTED_SEEDLING,true",
    "PRODUCT_POT,false",
    "PRODUCT_POT,true",
    "SAMPLE,false",
    "SAMPLE,true",
    "ETC,false",
    "ETC,true"
  })
  void replaysEachInboundTypeWithExistingOrNewVariety(InboundType type, boolean newVariety)
      throws Exception {
    var request = request(type, newVariety);
    var first = createHttp(request, "types");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    var before = snapshot();
    var retry = createHttp(request, "types");
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertThat(count("inbound_records")).isEqualTo(1);
    assertThat(count("inbound_creation_receipts")).isEqualTo(1);
    assertThat(count("varieties")).isEqualTo(newVariety ? 2 : 1);
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(count("work_operation_targets")).isEqualTo(1);
    assertThat(count("work_target_executions")).isEqualTo(1);
    assertThat(count("work_applied_effects")).isEqualTo(1);
    assertThat(jdbc.queryForObject("select status from work_operations", String.class))
        .isEqualTo("COMPLETED");
    if (type != InboundType.FLASK_SEEDLING) {
      assertThat(first.data().path("createdOrchidGroups").get(0).path("id").asLong()).isPositive();
      assertThat(jdbc.queryForObject("select mutation_id from work_applied_effects", Long.class))
          .isPositive();
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = InboundType.class,
      names = {"FLASK_SEEDLING", "PRODUCT_POT"})
  void cancellationDoesNotRecreateInboundOrRestoreFirstCapability(InboundType type)
      throws Exception {
    var request = request(type, false);
    var first = createHttp(request, "cancelled");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(201);
    long id = first.data().path("id").asLong();
    inbounds.cancel(id, new InboundRecordCancelRequest("cancel-existing", "오입고"));
    var before = snapshot();
    var retry = createHttp(request, "cancelled");
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select status from inbound_records where id = ?", String.class, id))
        .isEqualTo("CANCELED");
  }

  @Test
  void replayKeepsOriginalInboundSnapshotAfterMetadataEdit() throws Exception {
    var request = flask(10, null);
    var first = createHttp(request, "edited");
    assertThat(first.status()).isEqualTo(201);
    long id = first.data().path("id").asLong();
    inbounds.update(
        id, new InboundRecordUpdateRequest(DATE, 20, "수정 위치", DATE.plusDays(8), "수정 담당", "후속 변경"));
    var before = snapshot();
    var retry = createHttp(request, "edited");
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select estimated_quantity from inbound_records where id = ?", Integer.class, id))
        .isEqualTo(20);
  }

  @Test
  void replayAfterPottingDoesNotAddInboundWorkOrCurrentResultGroups() throws Exception {
    var request = flask(10, null);
    var first = createHttp(request, "potted");
    assertThat(first.status()).isEqualTo(201);
    long id = first.data().path("id").asLong();
    potting.executeNow(
        new InboundPottingCommand(
            "pot-existing",
            id,
            DATE,
            List.of(
                new InboundPottingResultInput(
                    zoneId,
                    10,
                    "3.5치",
                    1,
                    "POT",
                    null,
                    false,
                    BigDecimal.valueOf(12),
                    BigDecimal.valueOf(14),
                    null)),
            "포트 담당",
            "포트 완료"));
    var before = snapshot();
    var retry = createHttp(request, "potted");
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(retry.data().path("createdOrchidGroups")).isEmpty();
    assertThat(snapshot()).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "select status from inbound_records where id = ?", String.class, id))
        .isEqualTo("PLACED");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "inboundDate",
        "inboundType",
        "varietyId",
        "newVariety",
        "estimatedQuantity",
        "tempLocation",
        "pottingDueDate",
        "placement",
        "worker",
        "memo"
      })
  void changedFieldConflictsBeforeCurrentBusinessValidation(String field) throws Exception {
    var request = flask(10, null);
    assertThat(createHttp(request, "changed-field").status()).isEqualTo(201);
    var changed = (ObjectNode) objectMapper.valueToTree(request);
    switch (field) {
      case "inboundDate" -> changed.put(field, DATE.minusDays(1).toString());
      case "inboundType" -> changed.put(field, "PRODUCT_POT");
      case "varietyId" -> changed.put(field, -1);
      case "newVariety" ->
          changed.set(
              field, objectMapper.valueToTree(new InboundVarietyInput("속", "품종", null, null)));
      case "estimatedQuantity" -> changed.put(field, 11);
      case "tempLocation" -> changed.put(field, "새 임시 위치");
      case "pottingDueDate" -> changed.put(field, DATE.plusDays(8).toString());
      case "placement" -> changed.set(field, objectMapper.valueToTree(placement()));
      case "worker" -> changed.put(field, "새 담당");
      case "memo" -> changed.put(field, "새 메모");
      default -> throw new IllegalStateException(field);
    }
    var before = snapshot();
    var result =
        post(
            "/api/inbound-records", changed.toString(), Map.of("Idempotency-Key", "changed-field"));
    assertThat(result.status()).isEqualTo(409);
    assertThat(result.body().path("error").path("code").asText())
        .isEqualTo("INBOUND_CREATE_REQUEST_KEY_CONFLICT");
    assertThat(snapshot()).isEqualTo(before);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "long"})
  void rejectsInvalidKeysBeforeAnyCreation(String key) throws Exception {
    if (key.equals("long")) key = "x".repeat(101);
    var before = snapshot();
    var result = createHttp(flask(10, null), key);
    assertThat(result.status()).isEqualTo(400);
    assertThat(result.body().path("error").path("code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void invalidChangedBodyDoesNotRemovePreviouslySuccessfulReceipt() throws Exception {
    var request = flask(10, null);
    var first = createHttp(request, "valid");
    assertThat(first.status()).isEqualTo(201);
    var before = snapshot();
    assertThat(createHttp(flask(0, null), "valid").status()).isEqualTo(400);
    assertThat(snapshot()).isEqualTo(before);
    var retry = createHttp(request, "valid");
    assertThat(retry.status()).isEqualTo(201);
    assertThat(retry.data()).isEqualTo(first.data());
    assertThat(snapshot()).isEqualTo(before);
  }

  @Test
  void distinctKeysCreateTwoRealInboundFactsWithOneReusedNewVariety() {
    var request = request(InboundType.FLASK_SEEDLING, true);
    var first = inbounds.create(request, "first");
    var second = inbounds.create(request, "second");
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(second.varietyId()).isEqualTo(first.varietyId());
    assertThat(count("inbound_records")).isEqualTo(2);
    assertThat(count("work_operations")).isEqualTo(2);
    assertThat(count("varieties")).isEqualTo(2);
  }

  @Test
  void omittedKeysRetainLegacyIndependentInboundCreation() throws Exception {
    var body = objectMapper.writeValueAsString(flask(10, null));
    var first = post("/api/inbound-records", body);
    var second = post("/api/inbound-records", body);
    assertThat(first.status()).isEqualTo(201);
    assertThat(second.status()).isEqualTo(201);
    assertThat(second.data().path("id")).isNotEqualTo(first.data().path("id"));
    assertThat(count("inbound_records")).isEqualTo(2);
    assertThat(count("work_operations")).isEqualTo(2);
    assertThat(count("inbound_creation_receipts")).isZero();
  }

  @ParameterizedTest
  @EnumSource(
      value = InboundType.class,
      names = {"FLASK_SEEDLING", "PRODUCT_POT"})
  void receiptFailureRollsBackNewVarietyInboundWorkAndMutation(InboundType type) {
    var request = request(type, true);
    var before = snapshot();
    jdbc.execute(
        "alter table inbound_creation_receipts add constraint test_inbound_receipt_failure check (response_snapshot is null)");
    try {
      assertThatThrownBy(() -> inbounds.create(request, "late-failure"))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("test_inbound_receipt_failure");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.execute(
          "alter table inbound_creation_receipts drop constraint test_inbound_receipt_failure");
    }
    var success = inbounds.create(request, "late-failure");
    assertThat(success.id()).isPositive();
    assertThat(count("inbound_records")).isEqualTo(1);
    assertThat(count("work_operations")).isEqualTo(1);
    assertThat(count("varieties")).isEqualTo(2);
  }

  @Test
  void inactiveInboundWorkTypeRollsBackEarlierVarietyAndPlacedGroup() {
    var request = request(InboundType.PRODUCT_POT, true);
    jdbc.update("update work_types set is_active = false where code = 'INBOUND'");
    var before = snapshot();
    try {
      assertThatThrownBy(() -> inbounds.create(request, "failed-work"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("비활성화");
      assertThat(snapshot()).isEqualTo(before);
    } finally {
      jdbc.update("update work_types set is_active = true where code = 'INBOUND'");
    }
    assertThat(inbounds.create(request, "failed-work").createdOrchidGroups()).hasSize(1);
  }

  @ParameterizedTest
  @EnumSource(
      value = InboundType.class,
      names = {"FLASK_SEEDLING", "PRODUCT_POT"})
  void replayDoesNotReloadCurrentInboundVarietyWorkOrGroups(InboundType type) {
    var request = request(type, false);
    var first = inbounds.create(request, "query");
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();
    assertThat(inbounds.create(request, "query")).isEqualTo(first);
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    assertThat(statistics.getEntityLoadCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({
    "FLASK_SEEDLING,false,false",
    "FLASK_SEEDLING,false,true",
    "FLASK_SEEDLING,true,false",
    "PRODUCT_POT,false,false",
    "PRODUCT_POT,false,true",
    "PRODUCT_POT,true,false"
  })
  void concurrentRequestWaitsOnCreationClaimBeforeCreatingVarietyOrWork(
      InboundType type, boolean rollback, boolean changed) throws Exception {
    var request = request(type, true);
    var competing =
        changed
            ? new InboundRecordCreateCommand(
                request.inboundDate(),
                request.inboundType(),
                request.varietyId(),
                request.newVariety(),
                request.estimatedQuantity(),
                request.tempLocation(),
                request.pottingDueDate(),
                request.placement(),
                request.worker(),
                "다른 입력")
            : request;
    var held = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var winnerPid = new AtomicInteger();
    var loserPid = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var winner =
          pool.submit(
              () -> {
                try {
                  return new Outcome(
                      new TransactionTemplate(transactionManager)
                          .execute(
                              tx -> {
                                winnerPid.set(
                                    jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                                var response = inbounds.create(request, "concurrent");
                                held.countDown();
                                await(resume);
                                if (rollback)
                                  throw new IllegalStateException("simulated transaction rollback");
                                return response;
                              }),
                      null);
                } catch (RuntimeException error) {
                  return new Outcome(null, error);
                }
              });
      try {
        assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
        var loser =
            pool.submit(
                () -> {
                  try {
                    return new Outcome(
                        new TransactionTemplate(transactionManager)
                            .execute(
                                tx -> {
                                  jdbc.execute("set local lock_timeout = '15s'");
                                  loserPid.set(
                                      jdbc.queryForObject(
                                          "select pg_backend_pid()", Integer.class));
                                  started.countDown();
                                  return inbounds.create(competing, "concurrent");
                                }),
                        null);
                  } catch (RuntimeException error) {
                    return new Outcome(null, error);
                  }
                });
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
        boolean blocked = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
          blocked =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "select ? = any(pg_blocking_pids(?))",
                      Boolean.class,
                      winnerPid.get(),
                      loserPid.get()));
          if (blocked) break;
          Thread.sleep(25);
        }
        assertThat(blocked).isTrue();
        assertThat(
                jdbc.queryForObject(
                    "select query from pg_stat_activity where pid = ?",
                    String.class,
                    loserPid.get()))
            .contains("inbound_creation_receipts");
        assertThat(loser.isDone()).isFalse();
        resume.countDown();
        var first = winner.get(20, TimeUnit.SECONDS);
        var second = loser.get(20, TimeUnit.SECONDS);
        if (rollback) {
          assertThat(first.error()).isInstanceOf(IllegalStateException.class);
          assertThat(second.error()).isNull();
        } else if (changed) {
          assertThat(first.error()).isNull();
          assertThat(second.error()).isInstanceOf(ConflictException.class);
          assertThat(((ConflictException) second.error()).getCode())
              .isEqualTo("INBOUND_CREATE_REQUEST_KEY_CONFLICT");
        } else {
          assertThat(first.error()).isNull();
          assertThat(second.error()).isNull();
          assertThat(second.response()).isEqualTo(first.response());
        }
        assertThat(count("inbound_records")).isEqualTo(1);
        assertThat(count("inbound_creation_receipts")).isEqualTo(1);
        assertThat(count("varieties")).isEqualTo(2);
        assertThat(count("work_operations")).isEqualTo(1);
        assertThat(count("work_operation_targets")).isEqualTo(1);
        assertThat(count("work_target_executions")).isEqualTo(1);
        assertThat(count("work_applied_effects")).isEqualTo(1);
        assertThat(count("orchid_groups")).isEqualTo(type == InboundType.FLASK_SEEDLING ? 1 : 2);
      } finally {
        resume.countDown();
      }
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(error);
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Integer.class);
  }

  private Map<String, List<String>> snapshot() {
    var rows = new LinkedHashMap<String, List<String>>();
    for (String table :
        List.of(
            "inbound_creation_receipts",
            "inbound_records",
            "varieties",
            "orchid_groups",
            "orchid_group_mutations",
            "orchid_group_mutation_entries",
            "orchid_group_mutation_relations",
            "orchid_group_ledger_coverages",
            "orchid_group_lineage",
            "work_operations",
            "work_operation_targets",
            "work_target_executions",
            "work_applied_effects",
            "work_effect_orchid_groups",
            "work_command_receipts",
            "work_command_receipt_memberships",
            "audit_events")) {
      rows.put(
          table,
          jdbc.queryForList(
              "select to_jsonb(row)::text from " + table + " row order by 1", String.class));
    }
    return rows;
  }

  private ApiResult createHttp(InboundRecordCreateCommand request, String key) throws Exception {
    return post(
        "/api/inbound-records",
        objectMapper.writeValueAsString(request),
        Map.of("Idempotency-Key", key));
  }

  private InboundPlacementInput placement() {
    return new InboundPlacementInput(
        10, zoneId, "3.5치", 1, "POT", null, BigDecimal.valueOf(12), BigDecimal.valueOf(14));
  }

  private InboundRecordCreateCommand request(InboundType type, boolean newVariety) {
    return new InboundRecordCreateCommand(
        DATE,
        type,
        newVariety ? null : varietyId,
        newVariety ? new InboundVarietyInput("팔레놉시스", newVarietyName, "3.5치", "신규") : null,
        10,
        type == InboundType.FLASK_SEEDLING ? "배양실" : null,
        type == InboundType.FLASK_SEEDLING ? DATE.plusDays(7) : null,
        type == InboundType.FLASK_SEEDLING ? null : placement(),
        "입고 담당",
        null);
  }

  private record Outcome(InboundRecordResponse response, RuntimeException error) {}

  private InboundRecordCreateCommand flask(int quantity, String memo) {
    return new InboundRecordCreateCommand(
        DATE,
        InboundType.FLASK_SEEDLING,
        varietyId,
        null,
        quantity,
        "배양실",
        DATE.plusDays(7),
        null,
        "입고 담당",
        memo);
  }
}
