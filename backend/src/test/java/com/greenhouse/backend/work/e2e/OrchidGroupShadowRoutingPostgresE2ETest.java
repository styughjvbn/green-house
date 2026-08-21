package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationShadowService;
import com.greenhouse.backend.farm.application.orchid.mutation.UpdateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupShadowComparisonStatus;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupShadowComparisonRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Tag("work-e2e")
@TestPropertySource(properties = {
		"app.orchid-ledger.writer-mode=SHADOW",
		"app.orchid-ledger.writer-version=1.1.0"
})
class OrchidGroupShadowRoutingPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupCommandService orchidGroupCommandService;
	@Autowired private OrchidGroupRepository orchidGroupRepository;
	@Autowired private OrchidGroupMutationRepository mutationRepository;
	@Autowired private OrchidGroupShadowComparisonRepository comparisonRepository;
	@Autowired private OrchidGroupMutationShadowService shadowService;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
	}

	@Test
	void keepsLegacyAsSoleWriterAndPersistsEngineComparisonAfterCommit() {
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
						"shadow routing"));

		var updated = orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow();
		assertThat(updated.getQuantity()).isEqualTo(90);
		assertThat(updated.getStateRevision()).isNull();
		assertThat(mutationRepository.count()).isZero();
		assertThat(comparisonRepository.findAll())
				.singleElement()
				.satisfies(comparison -> {
					assertThat(comparison.getStatus())
							.isEqualTo(OrchidGroupShadowComparisonStatus.MATCHED);
					assertThat(comparison.getExpectedEntries()).hasSize(1);
					assertThat(comparison.getActualEntries()).hasSize(1);
					assertThat(comparison.getMismatches()).isEmpty();
				});
	}

	@Test
	void reportsLegacyPhysicalDeleteAsMismatchWithoutBlockingIt() {
		orchidGroupCommandService.delete(scenario.orchidGroupId());

		assertThat(orchidGroupRepository.findById(scenario.orchidGroupId())).isEmpty();
		assertThat(mutationRepository.count()).isZero();
		assertThat(comparisonRepository.findAll())
				.singleElement()
				.satisfies(comparison -> {
					assertThat(comparison.getStatus())
							.isEqualTo(OrchidGroupShadowComparisonStatus.MISMATCHED);
					assertThat(comparison.getMismatches()).isNotEmpty();
				});
	}

	@Test
	void enginePlanUsesDetachedStateAndDoesNotWriteLedgerOrCurrentState() {
		var current = orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow();
		var plan = shadowService.prepare(new UpdateOrchidGroupMutationCommand(
				OrchidGroupMutationSources.farmRequest(
						"SHADOW_TEST", scenario.orchidGroupId().toString(), "UPDATE"),
				scenario.orchidGroupId(),
				new OrchidGroupMutationDetails(
						current.getVariety().getId(), 91, current.getPotSize(), current.getAgeYear(),
						"관리", current.getPlacementType(), current.getTrayCount(),
						current.getSplitPlacementAllowed(), current.getStartPosition(),
						current.getEndPosition(), "detached plan"),
				LocalDate.of(2026, 8, 21),
				"detached plan"));

		assertThat(plan.entries().getFirst().afterState().quantity()).isEqualTo(91);
		assertThat(orchidGroupRepository.findById(scenario.orchidGroupId()).orElseThrow().getQuantity())
				.isEqualTo(100);
		assertThat(mutationRepository.count()).isZero();
		assertThat(comparisonRepository.count()).isZero();
	}

	@Test
	void enginePlanRejectionDoesNotRollBackLegacyTransaction() {
		jdbcTemplate.update(
				"UPDATE orchid_groups SET quantity = 0, status = '생성 취소' WHERE id = ?",
				scenario.orchidGroupId());

		orchidGroupCommandService.delete(scenario.orchidGroupId());

		assertThat(orchidGroupRepository.findById(scenario.orchidGroupId())).isEmpty();
		assertThat(mutationRepository.count()).isZero();
		assertThat(comparisonRepository.findAll())
				.singleElement()
				.satisfies(comparison -> {
					assertThat(comparison.getStatus())
							.isEqualTo(OrchidGroupShadowComparisonStatus.ENGINE_REJECTED);
					assertThat(comparison.getEngineError()).contains("변경 전후");
				});
	}
}
