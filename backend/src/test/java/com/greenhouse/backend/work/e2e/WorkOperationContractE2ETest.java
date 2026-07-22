package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkOperationContractE2ETest extends WorkE2ETestBase {

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
	void createsAndCompletesNormalWorkWithoutChangingItsApiContract() throws Exception {
		ApiResult created = post("/api/work-operations", """
				{
				  "workTypeId": %d,
				  "title": "E2E 농약 작업",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d],
				  "details": {"materialName": "E2E 살균제", "dilutionRatio": "1000배"},
				  "worker": "E2E 작업자"
				}
				""".formatted(scenario.pesticideWorkTypeId(), scenario.orchidGroupId()));

		assertThat(created.status()).isEqualTo(201);
		assertThat(created.data().path("status").asText()).isEqualTo("PLANNED");
		assertThat(created.data().path("targets")).hasSize(1);
		long operationId = created.data().path("id").asLong();
		long targetId = created.data().path("targets").get(0).path("id").asLong();

		ApiResult started = post("/api/work-operations/%d/start".formatted(operationId), "");
		assertThat(started.status()).isEqualTo(200);
		assertThat(started.data().path("status").asText()).isEqualTo("IN_PROGRESS");

		ApiResult completed = post(
				"/api/work-operations/%d/targets/%d/complete".formatted(operationId, targetId),
				"""
				{
				  "worker": "E2E 완료자",
				  "completedDate": "2026-07-15",
				  "resultDetails": {"weather": "맑음"}
				}
				""");
		assertThat(completed.status()).isEqualTo(200);
		assertThat(completed.data().path("status").asText()).isEqualTo("COMPLETED");
		assertThat(completed.data().path("targets").get(0).path("executionStatus").asText())
				.isEqualTo("COMPLETED");
		assertThat(completed.data().path("targets").get(0).path("resultDetails").path("weather").asText())
				.isEqualTo("맑음");

		ApiResult detail = get("/api/work-operations/%d".formatted(operationId));
		assertThat(detail.status()).isEqualTo(200);
		assertThat(detail.data().path("id").asLong()).isEqualTo(operationId);
		assertThat(detail.data().path("status").asText()).isEqualTo("COMPLETED");
		assertThat(detail.data().path("targets").get(0).path("executionStatus").asText())
				.isEqualTo("COMPLETED");
		assertThat(detail.data().path("targets").get(0).path("resultDetails").path("weather").asText())
				.isEqualTo("맑음");

		ApiResult list = get("/api/work-operations?view=ALL");
		assertThat(list.status()).isEqualTo(200);
		assertThat(list.data()).hasSize(1);
		JsonNode listed = list.data().get(0);
		assertThat(listed.path("id").asLong()).isEqualTo(operationId);
		assertThat(listed.path("targets").get(0).path("orchidGroupId").asLong())
				.isEqualTo(scenario.orchidGroupId());

		assertThat(count("work_operations")).isEqualTo(1);
		assertThat(count("work_operation_targets")).isEqualTo(1);
		assertThat(count("work_target_executions")).isEqualTo(1);
		assertThat(count("work_applied_effects")).isEqualTo(1);
	}

	@Test
	void previewsTargetsAndCreatesAnImmediatelyCompletedRecord() throws Exception {
		ApiResult workTypes = get("/api/work-types");
		assertThat(workTypes.status()).isEqualTo(200);
		assertThat(workTypes.data()).anyMatch(type ->
				type.path("id").asLong() == scenario.pesticideWorkTypeId()
						&& type.path("code").asText().equals("PESTICIDE"));

		ApiResult preview = post("/api/work-operations/target-preview", """
				{
				  "scopeType": "MANUAL_SELECTION",
				  "orchidGroupIds": [%d]
				}
				""".formatted(scenario.orchidGroupId()));
		assertThat(preview.status()).isEqualTo(200);
		assertThat(preview.data().path("orchidGroupCount").asInt()).isEqualTo(1);
		assertThat(preview.data().path("totalQuantity").asInt()).isEqualTo(100);
		assertThat(preview.data().path("targets").get(0).path("orchidGroupId").asLong())
				.isEqualTo(scenario.orchidGroupId());

		ApiResult recorded = post("/api/work-operations/record", """
				{
				  "workTypeId": %d,
				  "title": "E2E 즉시 작업 기록",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "ORCHID_GROUP",
				  "sourceScopeId": %d,
				  "details": {"materialName": "기록용 살균제"},
				  "worker": "E2E 기록자"
				}
				""".formatted(scenario.pesticideWorkTypeId(), scenario.orchidGroupId()));
		assertThat(recorded.status()).isEqualTo(201);
		assertThat(recorded.data().path("status").asText()).isEqualTo("COMPLETED");
		assertThat(recorded.data().path("targets").get(0).path("executionStatus").asText())
				.isEqualTo("COMPLETED");

		ApiResult history = get("/api/orchid-groups/%d/work-history".formatted(scenario.orchidGroupId()));
		assertThat(history.status()).isEqualTo(200);
		assertThat(history.data()).hasSize(1);
		assertThat(history.data().get(0).path("workOperationId").asLong())
				.isEqualTo(recorded.data().path("id").asLong());
	}

	@Test
	void transitionsOperationAndTargetStatuses() throws Exception {
		ApiResult operation = createOperation("E2E 상태 전이 작업");
		long operationId = operation.data().path("id").asLong();

		assertThat(post("/api/work-operations/%d/start".formatted(operationId), "")
				.data().path("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(post("/api/work-operations/%d/pause".formatted(operationId), "")
				.data().path("status").asText()).isEqualTo("PAUSED");
		assertThat(post("/api/work-operations/%d/resume".formatted(operationId), "")
				.data().path("status").asText()).isEqualTo("IN_PROGRESS");
		assertThat(post("/api/work-operations/%d/cancel".formatted(operationId), "")
				.data().path("status").asText()).isEqualTo("CANCELED");

		ApiResult targetOperation = createOperation("E2E 대상 상태 전이 작업");
		long targetOperationId = targetOperation.data().path("id").asLong();
		long targetId = targetOperation.data().path("targets").get(0).path("id").asLong();
		assertThat(post("/api/work-operations/%d/start".formatted(targetOperationId), "").status())
				.isEqualTo(200);
		ApiResult targetStarted = post(
				"/api/work-operations/%d/targets/%d/start".formatted(targetOperationId, targetId),
				"{\"worker\":\"E2E 대상 작업자\"}");
		assertThat(targetStarted.status()).isEqualTo(200);
		assertThat(targetStarted.data().path("targets").get(0).path("executionStatus").asText())
				.isEqualTo("IN_PROGRESS");

		ApiResult skipped = post(
				"/api/work-operations/%d/targets/%d/skip".formatted(targetOperationId, targetId),
				"{\"worker\":\"E2E 대상 작업자\",\"resultDetails\":{\"reason\":\"제외\"}}");
		assertThat(skipped.status()).isEqualTo(200);
		assertThat(skipped.data().path("targets").get(0).path("executionStatus").asText())
				.isEqualTo("SKIPPED");
		assertThat(skipped.data().path("status").asText()).isEqualTo("COMPLETED");

		ApiResult historyView = get("/api/work-operations?view=HISTORY");
		assertThat(historyView.status()).isEqualTo(200);
		assertThat(historyView.data()).hasSize(2);
	}

	private ApiResult createOperation(String title) throws Exception {
		return post("/api/work-operations", """
				{
				  "workTypeId": %d,
				  "title": "%s",
				  "plannedStartDate": "2026-07-15",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				""".formatted(scenario.pesticideWorkTypeId(), title, scenario.orchidGroupId()));
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
	}
}
