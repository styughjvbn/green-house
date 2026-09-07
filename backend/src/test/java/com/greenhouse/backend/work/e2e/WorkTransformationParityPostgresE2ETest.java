package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationRoutingPolicy;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Tag("work-e2e")
class WorkTransformationParityPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private OrchidGroupRepository groupRepository;
	@Autowired private WorkAppliedEffectRepository effectRepository;
	@Autowired private OrchidGroupStateChainMigrationService migrationService;
	@MockitoSpyBean private OrchidGroupMutationRoutingPolicy routingPolicy;
	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
		jdbcTemplate.update("UPDATE orchid_groups SET status = '관리' WHERE id = ?", scenario.orchidGroupId());
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				migrationService, groupRepository, UUID.randomUUID(), LocalDate.of(2026, 8, 20), "1.0.0");
	}

	@ParameterizedTest
	@CsvSource({"REPOT,false", "REPOT,true", "DIVIDE,false", "DIVIDE,true",
			"MERGE,false", "MERGE,true", "MOVEMENT,false", "MOVEMENT,true"})
	void preservesPreChangeAttributesResultOrderAndLineage(String code, boolean engine) throws Exception {
		when(routingPolicy.routesToEngine()).thenReturn(engine);
		long operationId = planAndStart(code);
		int secondQuantity = code.equals("DIVIDE") ? 80 : 68;
		String request = execution(secondQuantity, 6);
		String path = "/api/work-operations/%d/structure-change-executions".formatted(operationId);
		ApiResult completed = post(path, request);
		assertThat(completed.status()).as(completed.body().toString()).isEqualTo(201);
		assertThat(completed.data().path("status").asText()).isEqualTo("COMPLETED");
		assertThat(groupRepository.findById(scenario.orchidGroupId()).orElseThrow().getQuantity()).isZero();

		var effects = effectRepository.findByWorkOperationIdOrderByIdAsc(operationId);
		assertThat(effects).hasSize(1);
		var effect = effects.getFirst();
		var details = objectMapper.valueToTree(effect.getResultDetails());
		assertThat(details.path("executionKey").asText()).isEqualTo("parity-round");
		assertThat(details.path("inputQuantity").asInt()).isEqualTo(100);
		assertThat(details.path("remainingQuantity").asInt()).isZero();
		assertThat(details.path("sourceInputQuantities").path(scenario.orchidGroupId().toString()).asInt())
				.isEqualTo(100);
		assertThat(details.path("lossQuantity").asInt()).isEqualTo(code.equals("DIVIDE") ? 0 : 2);
		assertThat(details.path("results")).hasSize(2);
		List<Long> resultIds = List.of(details.path("results").get(0).path("orchidGroupId").asLong(),
				details.path("results").get(1).path("orchidGroupId").asLong());
		for (int index = 0; index < 2; index++) {
			var group = groupRepository.findById(resultIds.get(index)).orElseThrow();
			assertThat(group.getQuantity()).isEqualTo(index == 0 ? 30 : secondQuantity);
			assertThat(group.getStatus()).isEqualTo(index == 0 || code.equals("MOVEMENT") ? "관리" : "별도 보관");
			assertThat(group.getPotSizeCode().name()).isEqualTo(code.equals("MOVEMENT") ? "POT_3_5" : "POT_4");
			assertThat(group.getAgeYear()).isEqualTo(code.equals("MOVEMENT") ? 2 : 3);
			assertThat(details.path("results").get(index).path("purpose").asText())
					.isEqualTo(index == 0 || code.equals("MOVEMENT") ? "NORMAL" : "HELD");
			assertThat(details.path("resultOrchidGroupIds").get(index).asLong()).isEqualTo(resultIds.get(index));
		}
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_lineage WHERE work_operation_id = ?",
				Long.class, operationId)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_lineage WHERE work_operation_id = ? AND mutation_id IS NOT NULL",
				Long.class, operationId)).isEqualTo(engine ? 2L : 0L);
		assertThat(effect.getMutationId() != null).isEqualTo(engine);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void invalidSecondPlacementRollsBackSourcesResultsLineageAndEffects(boolean engine) throws Exception {
		when(routingPolicy.routesToEngine()).thenReturn(engine);
		long operationId = planAndStart("REPOT");
		long mutationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orchid_group_mutations", Long.class);
		ApiResult rejected = post("/api/work-operations/%d/structure-change-executions".formatted(operationId),
				execution(68, 5));
		assertThat(rejected.status()).isEqualTo(400);
		assertThat(groupRepository.findAll()).singleElement().satisfies(group -> {
			assertThat(group.getQuantity()).isEqualTo(100);
			assertThat(group.getStatus()).isEqualTo("관리");
			assertThat(group.getStateRevision()).isZero();
		});
		assertThat(effectRepository.findByWorkOperationIdOrderByIdAsc(operationId)).isEmpty();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orchid_group_lineage", Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orchid_group_mutations", Long.class))
				.isEqualTo(mutationCount);
	}

	private long planAndStart(String code) throws Exception {
		Long workTypeId = jdbcTemplate.queryForObject("SELECT id FROM work_types WHERE code = ?", Long.class, code);
		ApiResult planned = post("/api/work-operations", """
				{"workTypeId":%d,"title":"모드별 구조 변경","plannedStartDate":"2026-08-20",
				 "sourceScopeType":"MANUAL_SELECTION","sourceOrchidGroupIds":[%d]}
				""".formatted(workTypeId, scenario.orchidGroupId()));
		assertThat(planned.status()).isEqualTo(201);
		long id = planned.data().path("id").asLong();
		assertThat(post("/api/work-operations/%d/start".formatted(id), "").status()).isEqualTo(200);
		return id;
	}

	private String execution(int secondQuantity, int secondStart) {
		return """
				{"idempotencyKey":"parity-round","completedDate":"2026-08-20",
				 "sources":[{"sourceOrchidGroupId":%d,"inputQuantity":100}],
				 "results":[
				   {"bedZoneId":%d,"quantity":30,"sourceOrchidGroupIds":[%d],
				    "potSize":"4치","ageYear":3,"purpose":"NORMAL","startPosition":5,"endPosition":6},
				   {"bedZoneId":%d,"quantity":%d,"sourceOrchidGroupIds":[%d],
				    "potSize":"4치","ageYear":3,"purpose":"HELD","startPosition":%d,"endPosition":7}]}
				""".formatted(scenario.orchidGroupId(), scenario.bedZoneId(), scenario.orchidGroupId(),
				scenario.bedZoneId(), secondQuantity, scenario.orchidGroupId(), secondStart);
	}
}
