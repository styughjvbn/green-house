package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.OrchidGroupStateChainTestSupport;
import com.greenhouse.backend.farm.application.inbound.InboundRecordService;
import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCreateRequest;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.work.application.operation.InboundPottingOperationService;
import com.greenhouse.backend.work.dto.effect.InboundPottingExecutionRequest;
import com.greenhouse.backend.work.dto.effect.InboundPottingResultRequest;
import com.greenhouse.backend.work.repository.WorkAppliedEffectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

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
	void completesInboundPottingThroughEngineAfterActiveCutover() {
		var varietyId = orchidGroupRepository.findById(scenario.orchidGroupId())
				.orElseThrow()
				.getVariety()
				.getId();
		var inbound = inboundRecordService.create(new InboundRecordCreateRequest(
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
				new InboundPottingExecutionRequest(
						"active-potting-postgres",
						inbound.id(),
						LocalDate.of(2026, 8, 20),
						List.of(new InboundPottingResultRequest(
								scenario.bedZoneId(), 28, "2인치", 1, "트레이", 2, false,
								new BigDecimal("10"), new BigDecimal("11"), null)),
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
		assertThat(orchidGroupRepository.findById(groupId).orElseThrow().getStateRevision())
				.isEqualTo(1L);
		assertThat(effect.getMutationId()).isNotNull();
		assertThat(reconciliationService.reconcile().ready()).isTrue();
	}
}
