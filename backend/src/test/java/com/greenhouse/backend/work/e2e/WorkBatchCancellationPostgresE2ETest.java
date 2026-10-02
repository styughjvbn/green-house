package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupMovementService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupMoveRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class WorkBatchCancellationPostgresE2ETest extends WorkE2ETestBase {

	@Autowired
	WorkTestDataSeeder seeder;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	OrchidGroupMovementService movement;

	@Autowired
	OrchidGroupRepository groups;

	@Autowired
	OrchidGroupStateChainMigrationService migration;

	@Autowired
	OrchidGroupLedgerCutoverService cutover;

	@Autowired
	OrchidGroupLedgerReconciliationService reconciliation;

	private List<Long> workIds;

	private List<Long> sourceIds;

	private List<Long> occupants;

	private Long resultId;

	private Long discardId;

	private Long zone;

	private Long variety;

	private void prepare() throws Exception {
		prepare(false);
	}

	private void prepare(boolean outsideWork) throws Exception {
		seeder.resetKeepingSequences();
		var scenario = seeder.seedContractScenario();
		zone = scenario.bedZoneId();
		variety = jdbc.queryForObject("SELECT variety_id FROM orchid_groups WHERE id=?", Long.class,
				scenario.orchidGroupId());
		UUID key = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(migration, groups, key, LocalDate.of(2026, 8, 20),
				"1.0.0");
		cutover.execute(new OrchidGroupLedgerCutoverCommand(key, LocalDate.of(2026, 8, 20), "1.0.0", "1.1.0", true));
		Long second = createGroup(zone, 50, 6, 8);
		sourceIds = List.of(scenario.orchidGroupId(), second);
		Long outsideId = outsideWork
				? plan(jdbc.queryForObject("SELECT id FROM work_types WHERE code='PESTICIDE'", Long.class),
						List.of(sourceIds.getFirst()))
				: null;
		Long destination = jdbc.queryForObject("SELECT id FROM bed_zones WHERE id <> ? ORDER BY id LIMIT 1", Long.class,
				zone);
		movement.move(sourceIds.get(0),
				new OrchidGroupMoveRequest(destination, BigDecimal.ZERO, new BigDecimal("5"), "worker", null));
		movement.move(sourceIds.get(1),
				new OrchidGroupMoveRequest(destination, new BigDecimal("6"), new BigDecimal("8"), "worker", null));
		Long transformId = plan(scenario.repotWorkTypeId(), sourceIds);
		assertThat(post("/api/work-operations/" + transformId + "/start", "{}").status()).isEqualTo(200);
		var execution = post("/api/work-operations/" + transformId + "/structure-change-executions",
				"""
						{"idempotencyKey":"transform","completedDate":"2026-07-15",
						 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":100},{"sourceOrchidGroupId":%d,"inputQuantity":50}],
						 "results":[{"bedZoneId":%d,"quantity":150,"potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":10,"endPosition":13}]}
						"""
					.formatted(sourceIds.get(0), sourceIds.get(1), zone));
		assertThat(execution.status()).as(execution.body().toString()).isEqualTo(201);
		resultId = jdbc.queryForObject(
				"SELECT link.orchid_group_id FROM work_effect_orchid_groups link JOIN work_applied_effects effect ON effect.id=link.work_applied_effect_id WHERE effect.work_operation_id=? AND link.relation_type='RESULT'",
				Long.class, transformId);
		Long discardType = jdbc.queryForObject("SELECT id FROM work_types WHERE code='DISCARD'", Long.class);
		discardId = plan(discardType, List.of(resultId));
		assertThat(post("/api/work-operations/" + discardId + "/start", "{}").status()).isEqualTo(200);
		Long target = jdbc.queryForObject("SELECT id FROM work_operation_targets WHERE work_operation_id=?", Long.class,
				discardId);
		var discarded = post("/api/work-operations/%d/targets/%d/complete".formatted(discardId, target), """
				{"completedDate":"2026-07-15","resultDetails":{"discardQuantity":150,"reason":"오등록"}}
				""");
		assertThat(discarded.status()).as(discarded.body().toString()).isEqualTo(200);
		workIds = jdbc.queryForList("SELECT id FROM work_operations ORDER BY id", Long.class)
			.stream()
			.filter(id -> !id.equals(outsideId))
			.toList();
		occupants = List.of(createGroup(zone, 10, 0, 5), createGroup(zone, 10, 6, 8), createGroup(zone, 10, 10, 13));
	}

	@Test
	void cancelsTheWholeChainAndOriginalsWithoutRestoringOccupiedPositions() throws Exception {
		prepare();
		var single = post("/api/work-operations/" + discardId + "/cancel", """
				{"idempotencyKey":"single","reason":"오등록"}
				""");
		assertThat(single.status()).isGreaterThanOrEqualTo(400);
		long links = jdbc.queryForObject("SELECT count(*) FROM work_effect_orchid_groups", Long.class);
		var response = post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "batch"));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
		Long compensation = response.data().path("compensationMutationId").asLong();
		assertThat(jdbc.queryForList("SELECT status FROM work_operations", String.class)).containsOnly("VOIDED");
		for (Long id : List.of(sourceIds.get(0), sourceIds.get(1), resultId)) {
			assertThat(groups.findById(id)).get().satisfies(group -> {
				assertThat(group.getQuantity()).isZero();
				assertThat(group.getStatus()).isEqualTo("생성 취소");
			});
		}
		for (Long id : occupants)
			assertThat(groups.findById(id).orElseThrow().getQuantity()).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_effect_orchid_groups", Long.class)).isEqualTo(links);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM orchid_group_mutation_relations WHERE mutation_id=? AND relation_type='COMPENSATES'",
				Long.class, compensation))
			.isEqualTo(4);
		assertThat(
				jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE context_data->>'compensationMutationId'=?",
						Long.class, compensation.toString()))
			.isEqualTo(3);
		assertThat(reconciliation.reconcile().ready()).isTrue();
		var replay = post("/api/work-operations/cancel-batch",
				request(workIds.reversed(), sourceIds.reversed(), "batch"));
		assertThat(replay.status()).as(replay.body().toString()).isEqualTo(200);
		assertThat(replay.data().path("compensationMutationId").asLong()).isEqualTo(compensation);
		assertThat(post("/api/work-operations/cancel-batch", request(workIds, List.of(sourceIds.get(0)), "batch"))
			.status()).isEqualTo(409);
	}

	@Test
	void concurrentRetriesCreateOnlyOneCompensationAndAuditSet() throws Exception {
		prepare();
		String payload = request(workIds, sourceIds, "parallel");
		var start = new java.util.concurrent.CountDownLatch(1);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
			java.util.concurrent.Callable<ApiResult> task = () -> {
				start.await();
				return post("/api/work-operations/cancel-batch", payload);
			};
			var first = executor.submit(task);
			var second = executor.submit(task);
			start.countDown();
			var firstResult = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
			var secondResult = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
			assertThat(firstResult.status()).as(firstResult.body().toString()).isEqualTo(200);
			assertThat(secondResult.status()).as(secondResult.body().toString()).isEqualTo(200);
			assertThat(firstResult.data().path("compensationMutationId"))
				.isEqualTo(secondResult.data().path("compensationMutationId"));
		}
		assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations WHERE mutation_type='COMPENSATION'",
				Long.class))
			.isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM audit_events WHERE context_data ? 'compensationMutationId'", Long.class))
			.isEqualTo(3);
	}

	@Test
	void missingDownstreamWorkAndUnrelatedCancellationIdsCannotBypassValidation() throws Exception {
		prepare();
		var missing = post("/api/work-operations/cancel-batch",
				request(workIds.stream().filter(id -> !id.equals(discardId)).toList(), sourceIds, "missing"));
		assertThat(missing.status()).isGreaterThanOrEqualTo(400);
		var unrelated = post("/api/work-operations/cancel-batch", request(workIds, occupants, "unrelated"));
		assertThat(unrelated.status()).isGreaterThanOrEqualTo(400);
		assertUnchanged();
	}

	@Test
	void ordinaryRestorationStillChecksOccupiedOriginalPositions() throws Exception {
		prepare();
		var response = post("/api/work-operations/cancel-batch", request(workIds, List.of(), "restore"));
		assertThat(response.status()).isGreaterThanOrEqualTo(400);
		assertUnchanged();
	}

	@Test
	void unselectedRecordOnlyWorkStillBlocksTheBatch() throws Exception {
		prepare(true);
		var response = post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "external"));
		assertThat(response.status()).isGreaterThanOrEqualTo(400);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_operations WHERE status='VOIDED'", Long.class))
			.isZero();
	}

	@Test
	void failureAfterMutationFlushRollsBackGroupsWorkAndAudit() throws Exception {
		prepare();
		jdbc.execute("ALTER TABLE work_operations ADD CONSTRAINT test_batch_failure CHECK (status <> 'VOIDED')");
		try {
			var response = post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "rollback"));
			assertThat(response.status()).isGreaterThanOrEqualTo(400);
			assertUnchanged();
			assertThat(jdbc.queryForObject(
					"SELECT count(*) FROM audit_events WHERE context_data ? 'compensationMutationId'", Long.class))
				.isZero();
		}
		finally {
			jdbc.execute("ALTER TABLE work_operations DROP CONSTRAINT test_batch_failure");
		}
	}

	private void assertUnchanged() {
		assertThat(jdbc.queryForList("SELECT status FROM work_operations", String.class)).containsOnly("COMPLETED");
		assertThat(groups.findById(resultId).orElseThrow().getStatus()).isEqualTo("폐기");
		for (Long id : sourceIds)
			assertThat(groups.findById(id).orElseThrow().getStatus()).isEqualTo("종료");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations WHERE mutation_type='COMPENSATION'",
				Long.class))
			.isZero();
	}

	@Test
	void rejectsOverlappingPlacementsBetweenRestoredGroupsWithoutAnyPartialChanges() throws Exception {
		assertRestorationConflict(true, true);
	}

	@Test
	void rejectsDuplicateSortOrdersBetweenNonOverlappingRestoredGroups() throws Exception {
		assertRestorationConflict(false, true);
	}

	@Test
	void rejectsDuplicateSortOrderWithAnUnselectedActiveGroup() throws Exception {
		assertRestorationConflict(false, false);
	}

	@Test
	void allowsCreationCancellationInsteadOfRestoringMutuallyConflictingPositions() throws Exception {
		prepareIndependentMoves(true, true);
		var response = post("/api/work-operations/cancel-batch", request(workIds, sourceIds, "cancel-conflicting"));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
		assertThat(jdbc.queryForList("SELECT status FROM work_operations", String.class)).containsOnly("VOIDED");
		for (Long id : sourceIds) {
			assertThat(groups.findById(id).orElseThrow().getStatus()).isEqualTo("생성 취소");
		}
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	private void assertRestorationConflict(boolean overlap, boolean moveSecond) throws Exception {
		prepareIndependentMoves(overlap, moveSecond);
		var beforeGroups = jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id");
		var beforeWorks = jdbc.queryForList("SELECT * FROM work_operations ORDER BY id");
		var response = post("/api/work-operations/cancel-batch", request(workIds, List.of(), "restore-conflicting"));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(400);
		assertThat(jdbc.queryForList("SELECT * FROM orchid_groups ORDER BY id")).isEqualTo(beforeGroups);
		assertThat(jdbc.queryForList("SELECT * FROM work_operations ORDER BY id")).isEqualTo(beforeWorks);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM orchid_group_mutations WHERE mutation_type='COMPENSATION'",
				Long.class))
			.isZero();
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	private void prepareIndependentMoves(boolean overlap, boolean moveSecond) throws Exception {
		seeder.resetKeepingSequences();
		var scenario = seeder.seedContractScenario();
		zone = scenario.bedZoneId();
		variety = jdbc.queryForObject("SELECT variety_id FROM orchid_groups WHERE id=?", Long.class,
				scenario.orchidGroupId());
		UUID key = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(migration, groups, key, LocalDate.of(2026, 8, 20),
				"1.0.0");
		cutover.execute(new OrchidGroupLedgerCutoverCommand(key, LocalDate.of(2026, 8, 20), "1.0.0", "1.1.0", true));
		Long destination = jdbc.queryForObject("SELECT id FROM bed_zones WHERE id <> ? ORDER BY id LIMIT 1", Long.class,
				zone);
		movement.move(scenario.orchidGroupId(),
				new OrchidGroupMoveRequest(destination, BigDecimal.ZERO, new BigDecimal("5"), "worker", null));
		Long second = createGroup(zone, 10, overlap ? 0 : 6, overlap ? 5 : 11);
		if (moveSecond) {
			movement.move(second,
					new OrchidGroupMoveRequest(destination, new BigDecimal("6"), new BigDecimal("11"), "worker", null));
		}
		sourceIds = List.of(scenario.orchidGroupId(), second);
		workIds = jdbc.queryForList("SELECT id FROM work_operations ORDER BY id", Long.class);
		assertThat(reconciliation.reconcile().ready()).isTrue();
	}

	private String request(List<Long> ids, List<Long> finalIds, String key) throws Exception {
		return """
				{"workOperationIds":%s,"creationCancellationOrchidGroupIds":%s,"idempotencyKey":"%s","reason":"오등록 일괄 취소"}
				"""
			.formatted(objectMapper.writeValueAsString(ids), objectMapper.writeValueAsString(finalIds), key);
	}

	private Long plan(Long type, List<Long> ids) throws Exception {
		var response = post("/api/work-operations",
				"""
						{"workTypeId":%d,"title":"취소 검증","plannedStartDate":"2026-07-15","sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":%s}
						"""
					.formatted(type, objectMapper.writeValueAsString(ids)));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		return response.data().path("id").asLong();
	}

	private Long createGroup(Long targetZone, int quantity, int start, int end) throws Exception {
		var response = post("/api/orchid-groups",
				"""
						{"bedZoneId":%d,"varietyId":%d,"quantity":%d,"potSize":"4치","ageYear":2,"status":"정상","startPosition":%d,"endPosition":%d}
						"""
					.formatted(targetZone, variety, quantity, start, end));
		assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
		return response.data().path("id").asLong();
	}

}
