package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkUndoSafetyPostgresE2ETest extends WorkUndoSafetyTestBase {

	@org.springframework.beans.factory.annotation.Autowired
	org.springframework.transaction.PlatformTransactionManager transactionManager;

	@org.springframework.beans.factory.annotation.Autowired
	com.greenhouse.backend.farm.application.orchid.OrchidGroupMovementService movement;

	private long original() {
		return jdbc.queryForObject("select min(id) from work_operations", Long.class);
	}

	private long result() {
		return jdbc.queryForObject("select min(id) from orchid_groups where quantity = 60", Long.class);
	}

	private long pesticidePlan(boolean two) throws Exception {
		long type = jdbc.queryForObject("select id from work_types where code = 'PESTICIDE'", Long.class);
		String ids = two
				? String.join(",", jdbc
					.queryForList("select id::text from orchid_groups where quantity > 0 order by id", String.class))
				: Long.toString(result());
		var response = post("/api/work-operations", """
				{"workTypeId":%d,"title":"감사 재현","plannedStartDate":"2026-07-15",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%s]}
				""".formatted(type, ids));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		return response.data().path("id").asLong();
	}

	@Test
	void stoppedReferencesBlockBothSingleAndBatchUndo() throws Exception {
		long orig = original(), group = result(), stopped = pesticidePlan(false);
		assertThat(post("/api/work-operations/" + stopped + "/start", "{}").status()).isEqualTo(200);
		assertThat(post("/api/work-operations/" + stopped + "/end-remaining", "{}").status()).isEqualTo(200);
		var batch = post("/api/work-operations/cancel-batch", """
				{"workOperationIds":[%d],"idempotencyKey":"stopped-batch","reason":"audit"}
				""".formatted(orig));
		assertThat(batch.status()).as(batch.body().toString()).isEqualTo(400);
		var eligibility = get("/api/work-operations/" + orig + "/cancel-eligibility");
		assertThat(eligibility.status()).isEqualTo(200);
		assertThat(eligibility.data().path("cancellable").asBoolean()).isFalse();
		var cancel = post("/api/work-operations/" + orig + "/cancel", """
				{"idempotencyKey":"stopped-single","reason":"audit"}
				""");
		assertThat(cancel.status()).as(cancel.body().toString()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select quantity from orchid_groups where id = ?", Integer.class, group))
			.isEqualTo(60);
		assertThat(jdbc.queryForObject("select status from work_operations where id = ?", String.class, stopped))
			.isEqualTo("STOPPED");
	}

	@Test
	void singleUndoRejectsDifferentReasonOnSameKey() throws Exception {
		long orig = original();
		var first = post("/api/work-operations/" + orig + "/cancel", "{\"idempotencyKey\":\"same\",\"reason\":\"A\"}");
		var retry = post("/api/work-operations/" + orig + "/cancel", "{\"idempotencyKey\":\"same\",\"reason\":\"B\"}");
		assertThat(first.status()).isEqualTo(200);
		assertThat(retry.status()).isEqualTo(409);
	}

	@Test
	void batchUndoRejectsDifferentReasonOnSameKey() throws Exception {
		long orig = original();
		var first = post("/api/work-operations/cancel-batch",
				"{\"workOperationIds\":[" + orig + "],\"idempotencyKey\":\"same\",\"reason\":\"A\"}");
		var retry = post("/api/work-operations/cancel-batch",
				"{\"workOperationIds\":[" + orig + "],\"idempotencyKey\":\"same\",\"reason\":\"B\"}");
		assertThat(first.status()).as(first.body().toString()).isEqualTo(200);
		assertThat(retry.status()).as(retry.body().toString()).isEqualTo(409);
	}

	@Test
	void completeTargetAndCancelSerializeWithoutDeadlock() throws Exception {
		long operation = pesticidePlan(true);
		assertThat(post("/api/work-operations/" + operation + "/start", "{}").status()).isEqualTo(200);
		long target = jdbc.queryForObject("select min(id) from work_operation_targets where work_operation_id = ?",
				Long.class, operation);
		try (var connection = dataSource.getConnection(); var executor = Executors.newFixedThreadPool(2)) {
			connection.setAutoCommit(false);
			try (var statement = connection.prepareStatement(
					"select id from work_target_executions where work_operation_target_id = ? for update")) {
				statement.setLong(1, target);
				try (var rows = statement.executeQuery()) {
					assertThat(rows.next()).isTrue();
				}
			}
			var complete = executor
				.submit(() -> post("/api/work-operations/" + operation + "/targets/" + target + "/complete",
						"{\"completedDate\":\"2026-07-15\"}"));
			try {
				waitLocks(1);
				var cancel = executor.submit(() -> post("/api/work-operations/" + operation + "/cancel",
						"{\"idempotencyKey\":\"race\",\"reason\":\"audit\"}"));
				waitLocks(2);
				connection.commit();
				var c = complete.get(20, TimeUnit.SECONDS);
				var u = cancel.get(20, TimeUnit.SECONDS);
				assertThat(c.status()).as(c.body().toString()).isEqualTo(200);
				assertThat(u.status()).as(u.body().toString()).isEqualTo(200);
				assertThat(
						jdbc.queryForObject("select status from work_operations where id = ?", String.class, operation))
					.isEqualTo("CANCELED");
				assertThat(jdbc.queryForObject(
						"select count(*) from work_applied_effects where work_operation_id = ? and canceled_at is null",
						Long.class, operation))
					.isZero();
			}
			finally {
				connection.rollback();
			}
		}
	}

	private void waitLocks(int minimum) throws Exception {
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < until) {
			int count = jdbc.queryForObject(
					"select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
					Integer.class);
			if (count >= minimum)
				return;
			Thread.sleep(25);
		}
		throw new AssertionError("missing lock waiters " + minimum);
	}

	@Test
	void undoRevalidatesAfterAConcurrentMutationWithoutStaleEntityFailure() throws Exception {
		long orig = original(), group = result();
		long zone = jdbc.queryForObject("select bed_zone_id from orchid_groups where id = ?", Long.class, group);
		try (var executor = Executors.newSingleThreadExecutor()) {
			var pending = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<ApiResult>>();
			new org.springframework.transaction.support.TransactionTemplate(transactionManager)
				.executeWithoutResult(status -> {
					groups.findAllForUpdateByIdIn(java.util.List.of(group));
					pending.set(executor.submit(() -> post("/api/work-operations/" + orig + "/cancel",
							"{\"idempotencyKey\":\"mutation-race\",\"reason\":\"audit\"}")));
					try {
						waitLocks(1);
					}
					catch (Exception exception) {
						throw new RuntimeException(exception);
					}
					movement.move(group, new com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest(zone,
							java.math.BigDecimal.valueOf(12), java.math.BigDecimal.valueOf(14), "audit", null));
				});
			var undo = pending.get().get(20, TimeUnit.SECONDS);
			assertThat(undo.status()).as(undo.body().toString()).isEqualTo(400);
			assertThat(reconciliation.reconcile().ready()).isTrue();
		}
	}

}
