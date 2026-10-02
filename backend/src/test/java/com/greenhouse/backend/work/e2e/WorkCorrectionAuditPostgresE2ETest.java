package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkCorrectionAuditPostgresE2ETest extends WorkE2ETestBase {

	@Autowired
	WorkTestDataSeeder seeder;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationQueryService mutationQuery;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService mutationGraph;

	@Autowired
	com.greenhouse.backend.work.application.effect.WorkOrchidGroupLedgerRehearsalInspector rehearsal;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService reconciliation;

	private long originalId;

	private List<Long> resultIds;

	@BeforeEach
	void prepare() throws Exception {
		seeder.reset();
		var scenario = seeder.seedContractScenario();
		seeder.baselineGroups();
		var plan = post("/api/work-operations", """
				{"workTypeId":%d,"title":"보정 대상","plannedStartDate":"2026-07-15",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]}
				""".formatted(scenario.repotWorkTypeId(), scenario.orchidGroupId()));
		assertThat(plan.status()).as(plan.body().toString()).isEqualTo(201);
		originalId = plan.data().path("id").asLong();
		assertThat(post("/api/work-operations/" + originalId + "/start", "{}").status()).isEqualTo(200);
		var execution = post("/api/work-operations/" + originalId + "/structure-change-executions",
				"""
						{"idempotencyKey":"original","completedDate":"2026-07-15",
						 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":100}],
						 "results":[
						  {"bedZoneId":%d,"quantity":60,"potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":6,"endPosition":8},
						  {"bedZoneId":%d,"quantity":40,"potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":9,"endPosition":11}]}
						"""
					.formatted(scenario.orchidGroupId(), scenario.bedZoneId(), scenario.bedZoneId()));
		assertThat(execution.status()).as(execution.body().toString()).isEqualTo(201);
		resultIds = jdbc.queryForList("SELECT id FROM orchid_groups WHERE id <> ? ORDER BY id", Long.class,
				scenario.orchidGroupId());
	}

	@Test
	void concurrentDuplicateRequestsCreateOneAuditAndOneMutation() throws Exception {
		String request = request("same-key", 55, "2026-07-15");
		var responses = parallel(request, request);
		assertThat(responses).extracting(ApiResult::status).containsOnly(201);
		assertThat(count("work_operation_corrections")).isEqualTo(1);
		assertThat(count("work_correction_receipts")).isEqualTo(1);
		assertThat(count("work_operations")).isEqualTo(1);
		assertThat(count("work_applied_effects")).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM orchid_group_mutations WHERE source_type = 'WORK_CORRECTION'", Long.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				resultIds.getFirst()))
			.isEqualTo(55);
	}

	@Test
	void differentConcurrentRequestsSerializeAndPreserveBeforeValues() throws Exception {
		var responses = parallel(request("first", 55, "2026-07-15"), request("second", 50, "2026-07-15"));
		assertThat(responses).extracting(ApiResult::status).containsOnly(201);
		var history = get(path());
		var rows = history.data().path("corrections");
		assertThat(rows).hasSize(2);
		assertThat(rows.get(0).path("adjustments").get(0).path("beforeQuantity").asInt()).isEqualTo(60);
		assertThat(rows.get(1).path("adjustments").get(0).path("beforeQuantity").asInt())
			.isEqualTo(rows.get(0).path("adjustments").get(0).path("afterQuantity").asInt());
		assertThat(count("work_operations")).isEqualTo(1);
	}

	@Test
	void retriesAfterCancellationReturnTheSavedAudit() throws Exception {
		String request = request("date", 60, "2026-07-14");
		assertThat(post(path(), request).status()).isEqualTo(201);
		var canceled = post("/api/work-operations/" + originalId + "/cancel", """
				{"idempotencyKey":"cancel","reason":"실제로 하지 않은 작업"}
				""");
		assertThat(canceled.status()).as(canceled.body().toString()).isEqualTo(200);
		var retry = post(path(), request);
		assertThat(retry.status()).as(retry.body().toString()).isEqualTo(201);
		assertThat(retry.data().path("originalOperation").path("status").asText()).isEqualTo("VOIDED");
		assertThat(get("/api/work-operations/" + originalId + "/details").data().path("corrections")).hasSize(1);
	}

	@Test
	void bulkCorrectionKeepsOneOriginalAndSupportsFiltersAndGraphs() throws Exception {
		String request = """
				{"idempotencyKey":"bulk","workDate":"2026-07-15","worker":"작업자","memo":"검수","reason":"수량 확인",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":55,"status":"정상"},
				  {"orchidGroupId":%d,"quantity":35,"status":"정상"}]}
				""".formatted(resultIds.getFirst(), resultIds.getLast());
		var result = post(path(), request);
		assertThat(result.status()).as(result.body().toString()).isEqualTo(201);
		assertThat(result.data().path("originalOperation").path("status").asText()).isEqualTo("COMPLETED");
		assertThat(result.data().path("originalOperation").path("correctionCount").asLong()).isEqualTo(1);
		assertThat(result.data().path("corrections").get(0).path("adjustments")).hasSize(2);
		var filtered = get("/api/work-operations?hasCorrections=true");
		assertThat(filtered.data().path("totalElements").asLong()).isEqualTo(1);
		assertThat(filtered.data().path("content").get(0).path("relationSummary").path("linkedOperationCount").asInt())
			.isZero();
		assertThat(get("/api/work-operations?hasCorrections=false").data().path("totalElements").asLong()).isZero();
		assertThat(get("/api/work-operations/calendar?from=2026-07-01&to=2026-07-31&hasCorrections=true").data())
			.hasSize(1);
		var history = get("/api/work-history?historyScopeType=ORCHID_GROUP&historyScopeId=" + resultIds.getFirst());
		assertThat(history.data().path("totalElements").asLong()).isEqualTo(1);
		var graph = get("/api/work-operations/" + originalId + "/graph?detail=MUTATION");
		assertThat(graph.status()).as(graph.body().toString()).isEqualTo(200);
		long workNodes = java.util.stream.StreamSupport.stream(graph.data().path("nodes").spliterator(), false)
			.filter(node -> node.path("nodeType").asText().equals("WORK_OPERATION"))
			.count();
		assertThat(workNodes).isEqualTo(1);
		var cancellation = get("/api/work-operations/" + originalId + "/cancel-eligibility");
		assertThat(cancellation.data().path("cancellable").asBoolean()).isFalse();
		assertThat(count("work_applied_effects")).isEqualTo(1);
	}

	@Test
	void failedBulkCorrectionRollsBackReceiptAuditAndAllQuantityChanges() throws Exception {
		jdbc.execute("ALTER TABLE orchid_groups ADD CONSTRAINT test_correction_quantity CHECK (id <> "
				+ resultIds.getLast() + " OR quantity >= 40)");
		String invalid = """
				{"idempotencyKey":"retry","workDate":"2026-07-15","reason":"수량 확인",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":55,"status":"정상"},
				  {"orchidGroupId":%d,"quantity":35,"status":"정상"}]}
				""".formatted(resultIds.getFirst(), resultIds.getLast());
		ApiResult failed;
		try {
			failed = post(path(), invalid);
		}
		finally {
			jdbc.execute("ALTER TABLE orchid_groups DROP CONSTRAINT test_correction_quantity");
		}
		assertThat(failed.status()).as(failed.body().toString()).isBetween(400, 599);
		assertThat(count("work_operation_corrections")).isZero();
		assertThat(count("work_correction_receipts")).isZero();
		assertThat(jdbc.queryForObject("SELECT quantity FROM orchid_groups WHERE id = ?", Integer.class,
				resultIds.getFirst()))
			.isEqualTo(60);
		assertThat(post(path(), request("retry", 55, "2026-07-15")).status()).isEqualTo(201);
	}

	@Test
	void dateOnlyCorrectionHasNoMutationAndKeyReuseIsRejected() throws Exception {
		String request = """
				{"idempotencyKey":"date-only","workDate":"2026-07-14","reason":"날짜 정정","orchidGroupAdjustments":[]}
				""";
		assertThat(post(path(), request).status()).isEqualTo(201);
		assertThat(jdbc.queryForObject("SELECT mutation_id FROM work_operation_corrections", Long.class)).isNull();
		var conflict = post(path(), request.replace("2026-07-14", "2026-07-13"));
		assertThat(conflict.status()).isEqualTo(409);
		assertThat(conflict.body().path("error").path("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
	}

	@Test
	void rehearsalVerifiesAuditMutationProvenanceAndDetectsBrokenLinks() throws Exception {
		assertThat(post(path(), request("ledger", 55, "2026-07-15")).status()).isEqualTo(201);
		var mutations = mutationQuery.getMutations(resultIds.getFirst(),
				com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType.CORRECTION, null, 0, 20);
		assertThat(mutations.content()).singleElement().satisfies(mutation -> {
			assertThat(mutation.workOperation()).isNotNull();
			assertThat(mutation.workOperation().id()).isEqualTo(originalId);
		});
		var graph = mutationGraph.getGraph(resultIds.getFirst(), 0, 100);
		assertThat(graph.nodes())
			.filteredOn(node -> "CORRECTION".equals(node.mutationType() == null ? null : node.mutationType().name()))
			.singleElement()
			.satisfies(node -> assertThat(node.workOperation().id()).isEqualTo(originalId));
		var references = rehearsal.inspect().corrections();
		assertThat(references).hasSize(1);
		assertThat(references.getFirst().changesGroups()).isTrue();
		assertThat(references.getFirst().orchidGroupIds()).containsExactly(resultIds.getFirst());
		assertThat(reconciliation.reconcile().issues()).noneMatch(issue -> issue.code().contains("WORK_CORRECTION"));
		jdbc.update("UPDATE work_operation_corrections SET correlation_id = ?", java.util.UUID.randomUUID());
		assertThat(reconciliation.reconcile().issues())
			.anyMatch(issue -> issue.code().equals("INVALID_WORK_CORRECTION_MUTATION_LINK"));
	}

	private List<ApiResult> parallel(String first, String second) throws Exception {
		var ready = new CountDownLatch(2);
		var start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var futures = List.of(first, second).stream().map(request -> executor.submit(() -> {
				ready.countDown();
				if (!start.await(5, TimeUnit.SECONDS))
					throw new AssertionError("start timeout");
				return post(path(), request);
			})).toList();
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(futures.getFirst().get(20, TimeUnit.SECONDS), futures.getLast().get(20, TimeUnit.SECONDS));
		}
	}

	private String request(String key, int quantity, String date) {
		return """
				{"idempotencyKey":"%s","workDate":"%s","reason":"수량 확인",
				 "orchidGroupAdjustments":[{"orchidGroupId":%d,"quantity":%d,"status":"정상"}]}
				""".formatted(key, date, resultIds.getFirst(), quantity);
	}

	private String path() {
		return "/api/work-operations/" + originalId + "/corrections";
	}

	private long count(String table) {
		return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

}
