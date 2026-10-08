package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.support.OrchidGroupLedgerTestFixture;
import com.greenhouse.backend.work.api.effect.InboundPottingCommand;
import com.greenhouse.backend.work.api.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.WorkOperationProgressService;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = {"app.orchid-ledger.writer-version=2.0.0"})
class OrchidGroupMutationRoutingPostgresE2ETest extends WorkE2ETestBase {

  @Autowired private WorkTestDataSeeder seeder;

  @Autowired private OrchidGroupLedgerReconciliationService reconciliationService;

  @Autowired private OrchidGroupLedgerTestFixture ledgerFixture;

  @MockitoSpyBean private OrchidGroupCommandService orchidGroupCommandService;

  @Autowired private OrchidGroupRepository orchidGroupRepository;

  @Autowired private InboundRecordService inboundRecordService;

  @Autowired private InboundPottingOperationService inboundPottingOperationService;

  @Autowired private WorkAppliedEffectRepository workAppliedEffectRepository;

  @Autowired private WorkOperationProgressService progressService;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private EntityManager entityManager;

  @Autowired private OrchidGroupMutationEngine mutationEngine;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private DataSource dataSource;

  private WorkTestDataSeeder.ContractScenario scenario;

  @BeforeEach
  void setUp() {
    seeder.reset();
    scenario = seeder.seedContractScenario();
    UUID cutoverKey = UUID.randomUUID();
    ledgerFixture.seedBaseline(cutoverKey, LocalDate.of(2026, 8, 20), "1.0.0");
    ledgerFixture.activate(cutoverKey);
  }

  @Test
  void reverseCancellationThenCreationCancellationPreservesHistory() throws Exception {
    Long first = discardForCancellation("first");
    Long second = discardForCancellation("second");
    long links =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM work_effect_orchid_groups WHERE orchid_group_id = ?",
            Long.class,
            scenario.orchidGroupId());
    assertThatThrownBy(() -> orchidGroupCommandService.delete(scenario.orchidGroupId()))
        .isInstanceOf(ConflictException.class);
    assertThat(
            get("/api/work-operations/" + first + "/cancel-eligibility")
                .data()
                .path("cancellable")
                .asBoolean())
        .isFalse();
    for (Long id : List.of(second, first)) {
      var canceled =
          post(
              "/api/work-operations/" + id + "/cancel",
              """
					{"idempotencyKey":"cancel-%d","reason":"오등록 취소"}
					"""
                  .formatted(id));
      assertThat(canceled.status()).as(canceled.body().toString()).isEqualTo(200);
    }
    assertThat(orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getQuantity())
        .isEqualTo(100);
    orchidGroupCommandService.delete(scenario.orchidGroupId());
    var group = orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow();
    assertThat(group.getQuantity()).isZero();
    assertThat(group.getStatus()).isEqualTo("생성 취소");
    assertThat(
            jdbcTemplate.queryForMap(
                """
				SELECT action, before_data->>'quantity' AS before_quantity,
				       after_data->>'quantity' AS after_quantity, after_data->>'status' AS after_status
				FROM audit_events WHERE entity_type = 'ORCHID_GROUP' AND entity_id = ?
				  AND source = 'ORCHID_GROUP_MANAGEMENT' ORDER BY id DESC LIMIT 1
				""",
                scenario.orchidGroupId()))
        .containsEntry("action", "DEACTIVATED")
        .containsEntry("before_quantity", "100")
        .containsEntry("after_quantity", "0")
        .containsEntry("after_status", "생성 취소");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM work_effect_orchid_groups WHERE orchid_group_id = ?",
                Long.class,
                scenario.orchidGroupId()))
        .isEqualTo(links);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT status FROM work_operations WHERE id IN (?, ?)",
                String.class,
                first,
                second))
        .containsOnly("VOIDED");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orchid_group_mutations WHERE mutation_type = 'CANCEL_CREATION'",
                Long.class))
        .isEqualTo(1);
    assertThat(reconciliationService.reconcile().ready()).isTrue();
  }

  @Test
  void creationCancellationWaitsForTheGroupLock() throws Exception {
    var worker = new PostgresLockTestSupport.Worker();
    doAnswer(
            invocation -> {
              worker.capture(jdbcTemplate);
              return invocation.callRealMethod();
            })
        .when(orchidGroupCommandService)
        .delete(anyLong());
    try (var executor = Executors.newSingleThreadExecutor()) {
      var transaction = new TransactionTemplate(transactionManager);
      var deletion =
          transaction.execute(
              status -> {
                orchidGroupRepository.findAllForUpdateByIdIn(List.of(scenario.orchidGroupId()));
                int owner = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                // Observation must see the live blocking chain even after statistics were read.
                jdbcTemplate.queryForList("SELECT pid, wait_event_type FROM pg_stat_activity");
                var pending =
                    executor.submit(
                        () -> orchidGroupCommandService.delete(scenario.orchidGroupId()));
                try {
                  worker.awaitBlockedBy(jdbcTemplate, owner, pending);
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
                return pending;
              });
      deletion.get(10, TimeUnit.SECONDS);
      assertThat(orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getStatus())
          .isEqualTo("생성 취소");
      assertThat(reconciliationService.reconcile().ready()).isTrue();
    }
  }

  @Test
  void unrelatedLockWaitersCannotSatisfyWorkerObservation() throws Exception {
    var worker = new PostgresLockTestSupport.Worker();
    var unrelated = new PostgresLockTestSupport.Worker();
    try (var owner = dataSource.getConnection();
        var otherOwner = dataSource.getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      owner.setAutoCommit(false);
      otherOwner.setAutoCommit(false);
      int ownerPid = PostgresLockTestSupport.backendPid(owner);
      int otherPid = PostgresLockTestSupport.backendPid(otherOwner);
      try {
        try (var statement = owner.createStatement()) {
          statement.execute("SELECT pg_advisory_xact_lock(513801)");
        }
        try (var statement = otherOwner.createStatement()) {
          statement.execute("SELECT pg_advisory_xact_lock(513802)");
        }
        var pending =
            executor.submit(
                () ->
                    new TransactionTemplate(transactionManager)
                        .executeWithoutResult(
                            status -> {
                              worker.capture(jdbcTemplate);
                              jdbcTemplate.execute("SELECT pg_advisory_xact_lock(513801)");
                            }));
        var other =
            executor.submit(
                () ->
                    new TransactionTemplate(transactionManager)
                        .executeWithoutResult(
                            status -> {
                              unrelated.capture(jdbcTemplate);
                              jdbcTemplate.execute("SELECT pg_advisory_xact_lock(513802)");
                            }));
        worker.awaitBlockedBy(jdbcTemplate, ownerPid, pending);
        unrelated.awaitBlockedBy(jdbcTemplate, otherPid, other);
        assertThat(worker.isBlockedByOwner(jdbcTemplate, otherPid)).isFalse();
        assertThat(unrelated.isBlockedByOwner(jdbcTemplate, ownerPid)).isFalse();
        owner.commit();
        otherOwner.commit();
        pending.get(10, TimeUnit.SECONDS);
        other.get(10, TimeUnit.SECONDS);
      } finally {
        owner.rollback();
        otherOwner.rollback();
      }
    }
  }

  private Long discardForCancellation(String title) throws Exception {
    Long typeId =
        jdbcTemplate.queryForObject("SELECT id FROM work_types WHERE code = 'DISCARD'", Long.class);
    var planned =
        post(
            "/api/work-operations",
            """
				{"workTypeId":%d,"title":"%s","plannedStartDate":"2026-08-20",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]}
				"""
                .formatted(typeId, title, scenario.orchidGroupId()));
    assertThat(planned.status()).as(planned.body().toString()).isEqualTo(201);
    Long id = planned.data().path("id").asLong();
    Long targetId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM work_operation_targets WHERE work_operation_id = ?", Long.class, id);
    assertThat(post("/api/work-operations/" + id + "/start", "{}").status()).isEqualTo(200);
    var completed =
        post(
            "/api/work-operations/%d/targets/%d/complete".formatted(id, targetId),
            """
				{"completedDate":"2026-08-20","resultDetails":{"discardQuantity":10,"reason":"오등록"}}
				""");
    assertThat(completed.status()).as(completed.body().toString()).isEqualTo(200);
    return id;
  }

  @Test
  void routesFarmCommandThroughEngineAfterActiveCutoverAndFenceRejectsDirectWrite() {
    var current = orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow();
    orchidGroupCommandService.update(
        scenario.orchidGroupId(),
        new OrchidGroupUpdateRequest(
            current.getVariety().getId(),
            90,
            current.getPotSize(),
            current.getAgeYear(),
            "관리",
            current.getPlacementType(),
            current.getTrayCount(),
            current.getSplitPlacementAllowed(),
            current.getStartPosition(),
            current.getEndPosition(),
            "ACTIVE routing"));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT state_revision FROM orchid_groups WHERE id = ?",
                Long.class,
                scenario.orchidGroupId()))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT quantity FROM orchid_groups WHERE id = ?",
                Integer.class,
                scenario.orchidGroupId()))
        .isEqualTo(90);
    assertThat(reconciliationService.reconcile().ready()).isTrue();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE orchid_groups SET quantity = 80 WHERE id = ?",
                    scenario.orchidGroupId()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("Mutation context");
  }

  @Test
  void workDiscardRollsBackWithItsEffectAndRetriesWithTheSameMutationIdentity() throws Exception {
    Long discardWorkTypeId =
        jdbcTemplate.queryForObject("SELECT id FROM work_types WHERE code = 'DISCARD'", Long.class);
    ApiResult planned =
        post(
            "/api/work-operations",
            """
				{
				  "workTypeId": %d,
				  "title": "ACTIVE 폐기",
				  "plannedStartDate": "2026-08-20",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				"""
                .formatted(discardWorkTypeId, scenario.orchidGroupId()));
    assertThat(planned.status()).isEqualTo(201);
    long operationId = planned.data().path("id").asLong();
    Long targetId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM work_operation_targets WHERE work_operation_id = ?",
            Long.class,
            operationId);
    assertThat(post("/api/work-operations/%d/start".formatted(operationId), "").status())
        .isEqualTo(200);

    var transaction = new TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      progressService.completeTarget(
                          operationId,
                          targetId,
                          new WorkTargetExecutionRequest(
                              "폐기 담당",
                              Map.of("discardQuantity", 30, "reason", "폐기 사유"),
                              LocalDate.of(2026, 8, 21)));
                      entityManager.flush();
                      assertThat(
                              orchidGroupRepository
                                  .findById(scenario.orchidGroupId())
                                  .orElseThrow()
                                  .getQuantity())
                          .isEqualTo(70);
                      assertThat(
                              workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(
                                  operationId))
                          .singleElement()
                          .satisfies(effect -> assertThat(effect.getMutationId()).isNotNull());
                      throw new IllegalStateException("효과 저장 후 실패");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("효과 저장 후 실패");

    assertThat(orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getQuantity())
        .isEqualTo(100);
    assertThat(workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operationId))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orchid_group_mutations WHERE source_domain = 'WORK'",
                Long.class))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM work_target_executions WHERE work_operation_target_id = ?",
                String.class,
                targetId))
        .isEqualTo("PENDING");

    String completePath =
        "/api/work-operations/%d/targets/%d/complete".formatted(operationId, targetId);
    String request =
        """
				{"worker":"폐기 담당", "completedDate":"2026-08-21",
				 "resultDetails":{"discardQuantity":30,"reason":"폐기 사유"}}
				""";
    assertThat(post(completePath, request).status()).isEqualTo(200);
    assertThat(post(completePath, request).status()).isEqualTo(200);

    var group = orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow();
    assertThat(group.getQuantity()).isEqualTo(70);
    assertThat(group.getStateRevision()).isEqualTo(1L);
    var effects = workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
    assertThat(effects).hasSize(1);
    var effect = effects.getFirst();
    assertThat(effect.getEffectKey()).isEqualTo("TARGET:" + targetId);
    var mutation =
        jdbcTemplate.queryForMap(
            """
				SELECT source_domain, source_type, source_reference_id, source_operation_key,
				       effective_business_date, reason, correlation_id
				FROM orchid_group_mutations WHERE id = ?
				""",
            effect.getMutationId());
    assertThat(mutation)
        .containsEntry("source_domain", "WORK")
        .containsEntry("source_type", "WORK_EFFECT")
        .containsEntry("source_reference_id", Long.toString(operationId))
        .containsEntry("source_operation_key", effect.getEffectKey())
        .containsEntry("effective_business_date", Date.valueOf("2026-08-20"))
        .containsEntry("reason", "폐기 사유")
        .containsEntry("correlation_id", effect.getCorrelationId());
    assertThat(reconciliationService.reconcile().ready()).isTrue();
  }

  @Test
  void completesInboundPottingThroughEngineAfterActiveCutover() {
    var varietyId =
        orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getVariety().getId();
    var inbound =
        inboundRecordService.create(
            new InboundRecordCreateCommand(
                LocalDate.of(2026, 8, 19),
                InboundType.FLASK_SEEDLING,
                varietyId,
                null,
                30,
                "배양실",
                LocalDate.of(2026, 8, 20),
                null,
                "입고 담당",
                null));

    var operation =
        inboundPottingOperationService.executeNow(
            new InboundPottingCommand(
                "active-potting-postgres",
                inbound.id(),
                LocalDate.of(2026, 8, 20),
                List.of(
                    new InboundPottingResultInput(
                        scenario.bedZoneId(),
                        20,
                        "2인치",
                        1,
                        "트레이",
                        2,
                        false,
                        new BigDecimal("10"),
                        new BigDecimal("11"),
                        null),
                    new InboundPottingResultInput(
                        scenario.bedZoneId(),
                        8,
                        "2인치",
                        1,
                        "트레이",
                        1,
                        false,
                        new BigDecimal("11"),
                        new BigDecimal("12"),
                        null)),
                "포트 담당",
                "포트 완료"));

    var effect =
        workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operation.id()).stream()
            .filter(item -> item.getEffectKey().endsWith(":active-potting-postgres"))
            .findFirst()
            .orElseThrow();
    Long groupId =
        ((Number) ((List<?>) effect.getResultDetails().get("createdOrchidGroupIds")).getFirst())
            .longValue();
    assertThat((List<?>) effect.getResultDetails().get("createdOrchidGroupIds")).hasSize(2);
    assertThat(orchidGroupRepository.findById(groupId).orElseThrow().getStateRevision())
        .isEqualTo(1L);
    assertThat(effect.getMutationId()).isNotNull();
    assertThat(reconciliationService.reconcile().ready()).isTrue();
  }
}
