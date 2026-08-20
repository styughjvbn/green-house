package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.OrchidGroupCommandService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.dto.orchid.OrchidGroupUpdateRequest;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import java.time.LocalDate;
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
	@Autowired private OrchidGroupCommandService orchidGroupCommandService;
	@Autowired private OrchidGroupRepository orchidGroupRepository;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
		cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				UUID.randomUUID(),
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
}
