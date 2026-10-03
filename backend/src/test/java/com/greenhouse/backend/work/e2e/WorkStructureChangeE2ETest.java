package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkStructureChangeE2ETest extends WorkE2ETestBase {

	@Autowired
	private WorkTestDataSeeder seeder;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
		seeder.baselineGroups();
	}

	@Test
	void repotIsIdempotentAndPreservesQuantityLineageAndIntegratedHistory() throws Exception {
		String request = """
				{
				  "idempotencyKey": "e2e-repot-idempotency",
				  "title": "E2E 분갈이",
				  "workDate": "2026-07-15",
				  "worker": "E2E 작업자",
				  "sourceOrchidGroupId": %d,
				  "inputQuantity": 40,
				  "lossQuantity": 2,
				  "lossReason": "작업 손실",
				  "results": [{
				    "bedZoneId": %d,
				    "quantity": 38,
				    "potSize": "4치",
				    "ageYear": 3,
				    "startPosition": 6,
				    "endPosition": 8
				  }]
				}
				""".formatted(scenario.orchidGroupId(), scenario.bedZoneId());

		ApiResult first = post("/api/work-operations/repot", request);
		ApiResult duplicate = post("/api/work-operations/repot", request);

		assertThat(first.status()).isEqualTo(201);
		assertThat(duplicate.status()).isEqualTo(201);
		long operationId = first.data().path("operation").path("id").asLong();
		long resultGroupId = first.data().path("resultOrchidGroups").get(0).path("id").asLong();
		assertThat(duplicate.data().path("operation").path("id").asLong()).isEqualTo(operationId);
		assertThat(duplicate.data().path("resultOrchidGroups").get(0).path("id").asLong()).isEqualTo(resultGroupId);
		assertThat(first.data().path("sourceOrchidGroup").path("quantity").asInt()).isEqualTo(60);
		assertThat(first.data().path("resultOrchidGroups").get(0).path("quantity").asInt()).isEqualTo(38);
		assertThat(first.data().path("lossQuantity").asInt()).isEqualTo(2);

		assertThat(count("work_operations")).isEqualTo(1);
		assertThat(count("orchid_group_lineage")).isEqualTo(1);
		assertThat(count("work_applied_effects")).isEqualTo(1);
		assertThat(count("work_effect_orchid_groups")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				scenario.orchidGroupId()))
			.isEqualTo(60);

		ApiResult results = get("/api/work-operations/%d/repot-results".formatted(operationId));
		assertThat(results.status()).isEqualTo(200);
		assertThat(results.data().path("operation").path("id").asLong()).isEqualTo(operationId);
		assertThat(results.data().path("sourceOrchidGroup").path("quantity").asInt()).isEqualTo(60);
		assertThat(results.data().path("resultOrchidGroups").get(0).path("id").asLong()).isEqualTo(resultGroupId);
		assertThat(results.data().path("resultOrchidGroups").get(0).path("quantity").asInt()).isEqualTo(38);

		ApiResult history = get("/api/orchid-groups/%d/work-history".formatted(resultGroupId));
		assertThat(history.status()).isEqualTo(200);
		assertThat(history.data()).hasSize(1);
		assertThat(history.data().get(0).path("sourceKind").asText()).isEqualTo("WORK_OPERATION_EFFECT");
		assertThat(history.data().get(0).path("workOperationId").asLong()).isEqualTo(operationId);
	}

	@Test
	void executesAPlannedStructureChangeWithoutDuplicatingTheExecution() throws Exception {
		ApiResult planned = post("/api/work-operations", """
				{
				  "workTypeId": %d,
				  "title": "E2E 계획 분갈이",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				""".formatted(scenario.repotWorkTypeId(), scenario.orchidGroupId()));
		assertThat(planned.status()).isEqualTo(201);
		long operationId = planned.data().path("id").asLong();
		assertThat(post("/api/work-operations/%d/start".formatted(operationId), "").status()).isEqualTo(200);

		String executionRequest = """
				{
				  "idempotencyKey": "e2e-planned-repot-execution",
				  "completedDate": "2026-07-15",
				  "worker": "E2E 계획 작업자",
				  "sources": [{
				    "sourceOrchidGroupId": %d,
				    "inputQuantity": 40
				  }],
				  "lossQuantity": 0,
				  "results": [{
				    "bedZoneId": %d,
				    "quantity": 40,
				    "sourceOrchidGroupIds": [%d],
				    "potSize": "4치",
				    "ageYear": 3,
				    "purpose": "NORMAL",
				    "startPosition": 6,
				    "endPosition": 8
				  }]
				}
				""".formatted(scenario.orchidGroupId(), scenario.bedZoneId(), scenario.orchidGroupId());

		ApiResult first = post("/api/work-operations/%d/structure-change-executions".formatted(operationId),
				executionRequest);
		ApiResult duplicate = post("/api/work-operations/%d/structure-change-executions".formatted(operationId),
				executionRequest);

		assertThat(first.status()).isEqualTo(201);
		assertThat(duplicate.status()).isEqualTo(201);
		assertThat(first.data().path("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(first.data().path("targets").get(0).path("processedQuantity").asInt()).isEqualTo(40);
		assertThat(duplicate.data().path("id").asLong()).isEqualTo(operationId);
		assertThat(duplicate.data().path("targets").get(0).path("processedQuantity").asInt()).isEqualTo(40);
		assertThat(count("work_applied_effects")).isEqualTo(1);
		assertThat(count("orchid_group_lineage")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				scenario.orchidGroupId()))
			.isEqualTo(60);
	}

	@Test
	void preservesGroupIdsForOneToOneFullQuantityMovementAndRestoresThemOnVoid() throws Exception {
		WorkTestDataSeeder.MovementScenario movement = seeder.seedMovementScenario();
		seeder.baselineGroups();
		ApiResult planned = post("/api/work-operations", """
				{
				  "workTypeId": %d,
				  "title": "E2E 난 묶음 유지 자리 교환",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d, %d]
				}
				""".formatted(movement.movementWorkTypeId(), movement.firstOrchidGroupId(),
				movement.secondOrchidGroupId()));
		assertThat(planned.status()).isEqualTo(201);
		long operationId = planned.data().path("id").asLong();
		assertThat(post("/api/work-operations/%d/start".formatted(operationId), "").status()).isEqualTo(200);

		ApiResult completed = post("/api/work-operations/%d/structure-change-executions".formatted(operationId), """
				{
				  "idempotencyKey": "e2e-identity-movement",
				  "completedDate": "2026-07-15",
				  "sources": [
				    {"sourceOrchidGroupId": %d, "inputQuantity": 10},
				    {"sourceOrchidGroupId": %d, "inputQuantity": 20}
				  ],
				  "results": [
				    {"bedZoneId": %d, "quantity": 10, "attributeSourceOrchidGroupId": %d,
				     "purpose": "NORMAL", "startPosition": 1, "endPosition": 3},
				    {"bedZoneId": %d, "quantity": 20, "attributeSourceOrchidGroupId": %d,
				     "purpose": "NORMAL", "startPosition": 0, "endPosition": 1}
				  ]
				}
				""".formatted(movement.firstOrchidGroupId(), movement.secondOrchidGroupId(), movement.bedZoneId(),
				movement.firstOrchidGroupId(), movement.bedZoneId(), movement.secondOrchidGroupId()));

		assertThat(completed.status()).as(completed.body().toString()).isEqualTo(201);
		assertThat(completed.data().path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completed.data().path("targets").get(0).path("resultDetails").path("identityPreserved").asBoolean())
			.isTrue();
		assertThat(count("orchid_groups")).isEqualTo(3L);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orchid_groups WHERE id IN (?, ?)", Long.class,
				movement.firstOrchidGroupId(), movement.secondOrchidGroupId()))
			.isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				movement.firstOrchidGroupId()))
			.isEqualTo(10);
		assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				movement.secondOrchidGroupId()))
			.isEqualTo(20);
		assertThat(jdbcTemplate.queryForObject("SELECT start_position FROM orchid_groups WHERE id = ?", Double.class,
				movement.firstOrchidGroupId()))
			.isEqualTo(1.0);
		assertThat(jdbcTemplate.queryForObject("SELECT start_position FROM orchid_groups WHERE id = ?", Double.class,
				movement.secondOrchidGroupId()))
			.isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orchid_group_lineage WHERE work_operation_id = ?",
				Long.class, operationId))
			.isZero();
		assertThat(jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM orchid_group_mutation_entries entry
				JOIN orchid_group_mutations mutation ON mutation.id = entry.mutation_id
				WHERE mutation.source_reference_id = ? AND mutation.mutation_type = 'MOVE'
				""", Long.class, Long.toString(operationId))).isEqualTo(2L);

		ApiResult voided = post("/api/work-operations/%d/void".formatted(operationId),
				"{\"idempotencyKey\":\"e2e-void-identity-movement\",\"reason\":\"자리 교환 취소\"}");
		assertThat(voided.status()).isEqualTo(200);
		assertThat(voided.data().path("status").asText()).isEqualTo("VOIDED");
		assertThat(jdbcTemplate.queryForObject("SELECT start_position FROM orchid_groups WHERE id = ?", Double.class,
				movement.firstOrchidGroupId()))
			.isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT start_position FROM orchid_groups WHERE id = ?", Double.class,
				movement.secondOrchidGroupId()))
			.isEqualTo(1.0);
	}

	@Test
	void serializesConcurrentTargetCompletionAndAppliesDiscardOnlyOnce() throws Exception {
		Long discardWorkTypeId = jdbcTemplate.queryForObject("SELECT id FROM work_types WHERE code = 'DISCARD'",
				Long.class);
		ApiResult planned = post("/api/work-operations", """
				{
				  "workTypeId": %d,
				  "title": "E2E 동시 폐기",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				""".formatted(discardWorkTypeId, scenario.orchidGroupId()));
		assertThat(planned.status()).isEqualTo(201);
		long operationId = planned.data().path("id").asLong();
		Long targetId = jdbcTemplate.queryForObject("SELECT id FROM work_operation_targets WHERE work_operation_id = ?",
				Long.class, operationId);
		assertThat(post("/api/work-operations/%d/start".formatted(operationId), "").status()).isEqualTo(200);

		String path = "/api/work-operations/%d/targets/%d/complete".formatted(operationId, targetId);
		String request = """
				{
				  "worker": "E2E 폐기 담당자",
				  "resultDetails": {
				    "discardQuantity": 30,
				    "reason": "동시 요청 검증"
				  }
				}
				""";
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var tasks = List.<Callable<ApiResult>>of(
					() -> completeConcurrently(path, request, ready, start),
					() -> completeConcurrently(path, request, ready, start));
			var futures = tasks.stream().map(executor::submit).toList();
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			var responses = futures.stream().map(future -> {
				try {
					return future.get(10, TimeUnit.SECONDS);
				}
				catch (Exception exception) {
					throw new AssertionError(exception);
				}
			}).toList();

			assertThat(responses).extracting(ApiResult::status).containsExactlyInAnyOrder(200, 200);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				scenario.orchidGroupId()))
			.isEqualTo(70);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_applied_effects WHERE work_operation_id = ?",
				Long.class, operationId))
			.isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM work_target_executions WHERE work_operation_target_id = ? AND effect_applied_at IS NOT NULL",
				Long.class, targetId))
			.isEqualTo(1L);
	}

	private ApiResult completeConcurrently(String path, String request, CountDownLatch ready, CountDownLatch start)
			throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("동시 완료 요청 시작 신호를 기다리지 못했습니다.");
		}
		return post(path, request);
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
	}

}
