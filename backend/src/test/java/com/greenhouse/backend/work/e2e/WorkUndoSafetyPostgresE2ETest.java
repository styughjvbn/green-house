package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

import com.greenhouse.backend.support.MovementTestSupport;
import com.greenhouse.backend.support.MovementTestSupport.MoveTestRequest;
import com.greenhouse.backend.work.operation.application.WorkOperationProgressService;
import com.greenhouse.backend.work.operation.application.WorkOperationVoidService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@org.springframework.context.annotation.Import(MovementTestSupport.class)
class WorkUndoSafetyPostgresE2ETest extends WorkUndoSafetyTestBase {

  @MockitoSpyBean WorkOperationVoidService cancellations;
  @MockitoSpyBean WorkOperationProgressService progress;

  @org.springframework.beans.factory.annotation.Autowired
  PlatformTransactionManager transactionManager;

  @org.springframework.beans.factory.annotation.Autowired MovementTestSupport movement;

  private long original() {
    return jdbc.queryForObject("select min(id) from work_operations", Long.class);
  }

  private long result() {
    return jdbc.queryForObject("select min(id) from orchid_groups where quantity = 60", Long.class);
  }

  private long pesticidePlan(boolean two) throws Exception {
    long type =
        jdbc.queryForObject("select id from work_types where code = 'PESTICIDE'", Long.class);
    String ids =
        two
            ? String.join(
                ",",
                jdbc.queryForList(
                    "select id::text from orchid_groups where quantity > 0 order by id",
                    String.class))
            : Long.toString(result());
    var response =
        post(
            "/api/work-operations",
            """
				{"workTypeId":%d,"title":"감사 재현","plannedStartDate":"2026-07-15",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%s]}
				"""
                .formatted(type, ids));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
    return response.data().path("id").asLong();
  }

  @Test
  void stoppedReferencesBlockBothSingleAndBatchUndo() throws Exception {
    long orig = original(), group = result(), stopped = pesticidePlan(false);
    assertThat(post("/api/work-operations/" + stopped + "/start", "{}").status()).isEqualTo(200);
    assertThat(post("/api/work-operations/" + stopped + "/end-remaining", "{}").status())
        .isEqualTo(200);
    var batch =
        post(
            "/api/work-operations/cancel-batch",
            """
				{"workOperationIds":[%d],"idempotencyKey":"stopped-batch","reason":"audit"}
				"""
                .formatted(orig));
    assertThat(batch.status()).as(batch.body().toString()).isEqualTo(400);
    var eligibility = get("/api/work-operations/" + orig + "/cancel-eligibility");
    assertThat(eligibility.status()).isEqualTo(200);
    assertThat(eligibility.data().path("cancellable").asBoolean()).isFalse();
    var cancel =
        post(
            "/api/work-operations/" + orig + "/cancel",
            """
				{"idempotencyKey":"stopped-single","reason":"audit"}
				""");
    assertThat(cancel.status()).as(cancel.body().toString()).isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "select quantity from orchid_groups where id = ?", Integer.class, group))
        .isEqualTo(60);
    assertThat(
            jdbc.queryForObject(
                "select status from work_operations where id = ?", String.class, stopped))
        .isEqualTo("STOPPED");
  }

  @Test
  void singleUndoRejectsDifferentReasonOnSameKey() throws Exception {
    long orig = original();
    var first =
        post(
            "/api/work-operations/" + orig + "/cancel",
            "{\"idempotencyKey\":\"same\",\"reason\":\"A\"}");
    var retry =
        post(
            "/api/work-operations/" + orig + "/cancel",
            "{\"idempotencyKey\":\"same\",\"reason\":\"B\"}");
    assertThat(first.status()).isEqualTo(200);
    assertThat(retry.status()).isEqualTo(409);
  }

  @Test
  void batchUndoRejectsDifferentReasonOnSameKey() throws Exception {
    long orig = original();
    var first =
        post(
            "/api/work-operations/cancel-batch",
            "{\"workOperationIds\":[" + orig + "],\"idempotencyKey\":\"same\",\"reason\":\"A\"}");
    var retry =
        post(
            "/api/work-operations/cancel-batch",
            "{\"workOperationIds\":[" + orig + "],\"idempotencyKey\":\"same\",\"reason\":\"B\"}");
    assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
    assertThat(retry.status()).as(retry.body().toString()).isEqualTo(409);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void completeTargetAndCancelSerializeWithoutDeadlock(boolean completeFirst) throws Exception {
    long operation = pesticidePlan(true);
    assertThat(post("/api/work-operations/" + operation + "/start", "{}").status()).isEqualTo(200);
    long target =
        jdbc.queryForObject(
            "select min(id) from work_operation_targets where work_operation_id = ?",
            Long.class,
            operation);
    var completionWorker = new PostgresLockTestSupport.Worker();
    var cancellationWorker = cancellationWorker();
    doAnswer(
            invocation -> {
              completionWorker.capture(jdbc);
              return invocation.callRealMethod();
            })
        .when(progress)
        .completeTarget(anyLong(), anyLong(), any());
    try (var connection = dataSource.getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      int owner = PostgresLockTestSupport.backendPid(connection);
      try (var statement =
          connection.prepareStatement(
              "select id from work_target_executions where work_operation_target_id = ? for update")) {
        statement.setLong(1, target);
        try (var rows = statement.executeQuery()) {
          assertThat(rows.next()).isTrue();
        }
      }
      String completePath =
          "/api/work-operations/" + operation + "/targets/" + target + "/complete";
      String completeBody = "{\"completedDate\":\"2026-07-15\"}";
      String cancelPath = "/api/work-operations/" + operation + "/cancel";
      String cancelBody = "{\"idempotencyKey\":\"race\",\"reason\":\"audit\"}";
      var first =
          executor.submit(
              () ->
                  post(
                      completeFirst ? completePath : cancelPath,
                      completeFirst ? completeBody : cancelBody));
      try {
        (completeFirst ? completionWorker : cancellationWorker).awaitBlockedBy(jdbc, owner, first);
        var second =
            executor.submit(
                () ->
                    post(
                        completeFirst ? cancelPath : completePath,
                        completeFirst ? cancelBody : completeBody));
        (completeFirst ? cancellationWorker : completionWorker).awaitBlockedBy(jdbc, owner, second);
        connection.commit();
        var firstResult = first.get(20, TimeUnit.SECONDS);
        var secondResult = second.get(20, TimeUnit.SECONDS);
        assertThat(firstResult.status()).as(firstResult.body().toString()).isEqualTo(200);
        assertThat(secondResult.status())
            .as(secondResult.body().toString())
            .isEqualTo(completeFirst ? 200 : 400);
        if (!completeFirst) {
          assertThat(secondResult.body().path("error").path("code").asText())
              .isEqualTo("VALIDATION_ERROR");
        }
        assertThat(
                jdbc.queryForObject(
                    "select status from work_operations where id = ?", String.class, operation))
            .isEqualTo("CANCELED");
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from work_applied_effects where work_operation_id = ? and canceled_at is null",
                    Long.class,
                    operation))
            .isZero();
      } finally {
        connection.rollback();
      }
    }
  }

  private PostgresLockTestSupport.Worker cancellationWorker() {
    var worker = new PostgresLockTestSupport.Worker();
    doAnswer(
            invocation -> {
              worker.capture(jdbc);
              return invocation.callRealMethod();
            })
        .when(cancellations)
        .cancelOperation(anyLong(), any());
    return worker;
  }

  @Test
  void undoRevalidatesAfterAConcurrentMutationWithoutStaleEntityFailure() throws Exception {
    long orig = original(), group = result();
    long zone =
        jdbc.queryForObject(
            "select bed_zone_id from orchid_groups where id = ?", Long.class, group);
    var cancellationWorker = cancellationWorker();
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = new AtomicReference<Future<ApiResult>>();
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                groups.findAllForUpdateByIdIn(List.of(group));
                pending.set(
                    executor.submit(
                        () ->
                            post(
                                "/api/work-operations/" + orig + "/cancel",
                                "{\"idempotencyKey\":\"mutation-race\",\"reason\":\"audit\"}")));
                try {
                  cancellationWorker.awaitBlockedBy(
                      jdbc,
                      jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class),
                      pending.get());
                } catch (Exception exception) {
                  throw new RuntimeException(exception);
                }
                movement.move(
                    group,
                    new MoveTestRequest(
                        zone, BigDecimal.valueOf(12), BigDecimal.valueOf(14), "audit", null));
              });
      var undo = pending.get().get(20, TimeUnit.SECONDS);
      assertThat(undo.status()).as(undo.body().toString()).isEqualTo(400);
      assertThat(reconciliation.reconcile().ready()).isTrue();
    }
  }
}
