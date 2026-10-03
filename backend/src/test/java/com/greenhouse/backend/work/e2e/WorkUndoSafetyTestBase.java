package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
abstract class WorkUndoSafetyTestBase extends WorkE2ETestBase {

	@Autowired
	WorkTestDataSeeder seeder;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	javax.sql.DataSource dataSource;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationQueryService mutationQuery;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationGraphQueryService mutationGraph;

	@Autowired
	com.greenhouse.backend.work.application.effect.WorkOrchidGroupLedgerRehearsalInspector rehearsal;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService reconciliation;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService migration;

	@Autowired
	com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService cutover;

	@Autowired
	com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository groups;

	private long originalId;

	private List<Long> resultIds;

	@BeforeEach
	void prepare() throws Exception {
		seeder.resetKeepingSequences();
		var scenario = seeder.seedContractScenario();
		var key = java.util.UUID.randomUUID();
		var date = java.time.LocalDate.of(2026, 8, 20);
		com.greenhouse.backend.OrchidGroupStateChainTestSupport.importCurrentGroups(migration, groups, key, date,
				"1.0.0");
		cutover.execute(new com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand(key,
				date, "1.0.0", "1.1.0", true));
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

}
