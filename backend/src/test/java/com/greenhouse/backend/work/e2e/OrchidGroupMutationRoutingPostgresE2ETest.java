package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.application.inbound.InboundRecordCreateCommand;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.application.operation.WorkOperationProgressService;
import com.greenhouse.backend.work.application.effect.InboundPottingCommand;
import com.greenhouse.backend.work.application.effect.InboundPottingResultInput;
import com.greenhouse.backend.work.dto.target.WorkTargetExecutionRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
@TestPropertySource(properties = {
		"app.orchid-ledger.writer-mode=ENGINE",
		"app.orchid-ledger.writer-version=1.1.0"
})
class OrchidGroupMutationRoutingPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupLedgerCutoverService cutoverService;
	@Autowired private OrchidGroupLedgerReconciliationService reconciliationService;
	@Autowired private OrchidGroupStateChainMigrationService stateChainMigrationService;
	@Autowired private OrchidGroupCommandService orchidGroupCommandService;
	@Autowired private OrchidGroupRepository orchidGroupRepository;
	@Autowired private InboundRecordService inboundRecordService;
	@Autowired private InboundPottingOperationService inboundPottingOperationService;
	@Autowired private WorkAppliedEffectRepository workAppliedEffectRepository;
	@Autowired private WorkOperationProgressService progressService;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private EntityManager entityManager;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateChainTestSupport.importCurrentGroups(
				stateChainMigrationService,
				orchidGroupRepository,
				cutoverKey,
				LocalDate.of(2026, 8, 20),
				"1.0.0");
		cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey,
				LocalDate.of(2026, 8, 20),
				"1.0.0",
				"1.1.0",
				true));
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

		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId())).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isEqualTo(90);
		assertThat(reconciliationService.reconcile().ready()).isTrue();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE orchid_groups SET quantity = 80 WHERE id = ?",
				scenario.orchidGroupId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("Mutation context");
	}

	@Test
	void createsMultipleGroupsUnderTheActiveFenceBeforePlacementQueriesCanFlush() {
		Long varietyId = orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getVariety().getId();
		var groups = List.of(5, 6).stream().map(start -> new CreateOrchidGroupMutationItem(
				scenario.bedZoneId(), new OrchidGroupMutationDetails(varietyId, 10, "3.5치", 2, "정상",
				"POT", null, false, BigDecimal.valueOf(start), BigDecimal.valueOf(start + 1), null))).toList();
		var command = new CreateOrchidGroupsMutationCommand(
				OrchidGroupMutationSources.farmRequest("BATCH_CREATE", "active-batch", "CREATE"),
				groups, LocalDate.of(2026, 8, 20), "다중 생성");
		var transaction = new TransactionTemplate(transactionManager);
		var result = transaction.execute(status -> mutationEngine.createMany(command));
		var replay = transaction.execute(status -> mutationEngine.createMany(command));

		assertThat(result.entries()).hasSize(2);
		assertThat(replay.mutationId()).isEqualTo(result.mutationId());
		assertThat(result.entries()).allSatisfy(entry -> {
			var group = orchidGroupRepository.findById(entry.orchidGroupId()).orElseThrow();
			assertThat(group.getQuantity()).isEqualTo(10);
			assertThat(group.getStateRevision()).isEqualTo(1L);
		});
		assertThat(reconciliationService.reconcile().ready()).isTrue();
	}

	@Test
	void workDiscardRollsBackWithItsEffectAndRetriesWithTheSameMutationIdentity() throws Exception {
		Long discardWorkTypeId = jdbcTemplate.queryForObject(
				"SELECT id FROM work_types WHERE code = 'DISCARD'", Long.class);
		ApiResult planned = post("/api/work-operations", """
				{
				  "workTypeId": %d,
				  "title": "ACTIVE 폐기",
				  "plannedStartDate": "2026-08-20",
				  "sourceScopeType": "MANUAL_SELECTION",
				  "sourceOrchidGroupIds": [%d]
				}
				""".formatted(discardWorkTypeId, scenario.orchidGroupId()));
		assertThat(planned.status()).isEqualTo(201);
		long operationId = planned.data().path("id").asLong();
		Long targetId = jdbcTemplate.queryForObject(
				"SELECT id FROM work_operation_targets WHERE work_operation_id = ?", Long.class, operationId);
		assertThat(post("/api/work-operations/%d/start".formatted(operationId), "").status()).isEqualTo(200);

		var transaction = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			progressService.completeTarget(operationId, targetId, new WorkTargetExecutionRequest(
					"폐기 담당", Map.of("discardQuantity", 30, "reason", "폐기 사유"),
					LocalDate.of(2026, 8, 21)));
			entityManager.flush();
			assertThat(orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getQuantity())
					.isEqualTo(70);
			assertThat(workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operationId))
					.singleElement().satisfies(effect -> assertThat(effect.getMutationId()).isNotNull());
			throw new IllegalStateException("효과 저장 후 실패");
		})).isInstanceOf(IllegalStateException.class).hasMessage("효과 저장 후 실패");

		assertThat(orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getQuantity())
				.isEqualTo(100);
		assertThat(workAppliedEffectRepository.findByWorkOperationIdOrderByIdAsc(operationId)).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations WHERE source_domain = 'WORK'",
				Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM work_target_executions WHERE work_operation_target_id = ?",
				String.class, targetId)).isEqualTo("PENDING");

		String completePath = "/api/work-operations/%d/targets/%d/complete".formatted(operationId, targetId);
		String request = """
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
		var mutation = jdbcTemplate.queryForMap("""
				SELECT source_domain, source_type, source_reference_id, source_operation_key,
				       effective_business_date, reason, correlation_id
				FROM orchid_group_mutations WHERE id = ?
				""", effect.getMutationId());
		assertThat(mutation).containsEntry("source_domain", "WORK")
				.containsEntry("source_type", "WORK_EFFECT")
				.containsEntry("source_reference_id", Long.toString(operationId))
				.containsEntry("source_operation_key", effect.getEffectKey())
				.containsEntry("effective_business_date", java.sql.Date.valueOf("2026-08-20"))
				.containsEntry("reason", "폐기 사유")
				.containsEntry("correlation_id", effect.getCorrelationId());
		assertThat(reconciliationService.reconcile().ready()).isTrue();
	}

	@Test
	void completesInboundPottingThroughEngineAfterActiveCutover() {
		var varietyId = orchidGroupRepository.findById(scenario.orchidGroupId())
				.orElseThrow()
				.getVariety()
				.getId();
		var inbound = inboundRecordService.create(new InboundRecordCreateCommand(
				LocalDate.of(2026, 8, 19),
				InboundType.FLASK_SEEDLING,
				varietyId,
				null,
				3,
				30,
				null,
				"배양실",
				LocalDate.of(2026, 8, 20),
				"2인치",
				1,
				null,
				null,
				null,
				null,
				null,
				null,
				InboundStatus.POTTING_PENDING,
				"입고 담당",
				null));

		var operation = inboundPottingOperationService.executeNow(
				new InboundPottingCommand(
						"active-potting-postgres",
						inbound.id(),
						LocalDate.of(2026, 8, 20),
						List.of(new InboundPottingResultInput(
								scenario.bedZoneId(), 20, "2인치", 1, "트레이", 2, false,
								new BigDecimal("10"), new BigDecimal("11"), null),
								new InboundPottingResultInput(
										scenario.bedZoneId(), 8, "2인치", 1, "트레이", 1, false,
										new BigDecimal("11"), new BigDecimal("12"), null)),
						"유묘",
						"포트 담당",
						"포트 완료"));

		var effect = workAppliedEffectRepository
				.findByWorkOperationIdOrderByIdAsc(operation.id())
				.stream()
				.filter(item -> item.getEffectKey().equals("POTTING:active-potting-postgres"))
				.findFirst()
				.orElseThrow();
		Long groupId = ((Number) ((List<?>) effect.getResultDetails()
				.get("createdOrchidGroupIds")).getFirst()).longValue();
		assertThat((List<?>) effect.getResultDetails().get("createdOrchidGroupIds")).hasSize(2);
		assertThat(orchidGroupRepository.findById(groupId).orElseThrow().getStateRevision())
				.isEqualTo(1L);
		assertThat(effect.getMutationId()).isNotNull();
		assertThat(reconciliationService.reconcile().ready()).isTrue();
	}
}
