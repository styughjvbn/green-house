package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

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
		assertThat(duplicate.data().path("resultOrchidGroups").get(0).path("id").asLong())
				.isEqualTo(resultGroupId);
		assertThat(first.data().path("sourceOrchidGroup").path("quantity").asInt()).isEqualTo(60);
		assertThat(first.data().path("resultOrchidGroups").get(0).path("quantity").asInt()).isEqualTo(38);
		assertThat(first.data().path("lossQuantity").asInt()).isEqualTo(2);

		assertThat(count("work_operations")).isEqualTo(1);
		assertThat(count("orchid_group_lineage")).isEqualTo(1);
		assertThat(count("work_applied_effects")).isEqualTo(1);
		assertThat(count("work_effect_orchid_groups")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, scenario.orchidGroupId()))
				.isEqualTo(60);

		ApiResult results = get("/api/work-operations/%d/repot-results".formatted(operationId));
		assertThat(results.status()).isEqualTo(200);
		assertThat(results.data().path("operation").path("id").asLong()).isEqualTo(operationId);
		assertThat(results.data().path("sourceOrchidGroup").path("quantity").asInt()).isEqualTo(60);
		assertThat(results.data().path("resultOrchidGroups").get(0).path("id").asLong())
				.isEqualTo(resultGroupId);
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
		assertThat(post("/api/work-operations/%d/start".formatted(operationId), "").status())
				.isEqualTo(200);

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
				""".formatted(
				scenario.orchidGroupId(), scenario.bedZoneId(), scenario.orchidGroupId());

		ApiResult first = post(
				"/api/work-operations/%d/structure-change-executions".formatted(operationId),
				executionRequest);
		ApiResult duplicate = post(
				"/api/work-operations/%d/structure-change-executions".formatted(operationId),
				executionRequest);

		assertThat(first.status()).isEqualTo(201);
		assertThat(duplicate.status()).isEqualTo(201);
		assertThat(first.data().path("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(first.data().path("targets").get(0).path("processedQuantity").asInt()).isEqualTo(40);
		assertThat(duplicate.data().path("id").asLong()).isEqualTo(operationId);
		assertThat(duplicate.data().path("targets").get(0).path("processedQuantity").asInt()).isEqualTo(40);
		assertThat(count("work_applied_effects")).isEqualTo(1);
		assertThat(count("orchid_group_lineage")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class, scenario.orchidGroupId()))
				.isEqualTo(60);
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
	}
}
