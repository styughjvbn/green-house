package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.mutation.BaselineOrchidGroupsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationStage;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class OrchidGroupLedgerReconciliationPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupLedgerPreparationService preparationService;
	@Autowired private OrchidGroupLedgerReconciliationService reconciliationService;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
	}

	@Test
	void rehearsesBaselineAndDetectsAnOutOfLedgerDatabaseUpdate() {
		var preBaselineReport = reconciliationService.reconcile();

		assertThat(preBaselineReport.stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.PRE_BASELINE);
		assertThat(preBaselineReport.ready()).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_ledger_coverages", Long.class)).isZero();

		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		preparationService.prepare(cutoverKey, businessDate, "postgres-rehearsal-test");
		preparationService.start(cutoverKey);
		preparationService.baselineBatch(new BaselineOrchidGroupsCommand(
				cutoverKey,
				"GROUPS-0001",
				List.of(scenario.orchidGroupId()),
				businessDate));

		var baselineReport = reconciliationService.reconcile();

		assertThat(baselineReport.stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING);
		assertThat(baselineReport.ready()).isTrue();
		assertThat(baselineReport.baselineFingerprint()).hasSize(64);
		assertThatThrownBy(() -> jdbcTemplate.update("""
				INSERT INTO orchid_group_ledger_coverages (
				  cutover_key, status, engine_schema_version, snapshot_schema_version,
				  effective_business_date, minimum_writer_version
				) VALUES (?, 'PREPARING', 1, 1, ?, 'competing-writer')
				""", UUID.randomUUID(), businessDate))
				.isInstanceOf(DataIntegrityViolationException.class);

		jdbcTemplate.update(
				"UPDATE orchid_groups SET reserved_quantity = 1 WHERE id = ?",
				scenario.orchidGroupId());

		var corruptedReport = reconciliationService.reconcile();

		assertThat(corruptedReport.ready()).isFalse();
		assertThat(corruptedReport.issues()).extracting("code")
				.contains("CURRENT_SNAPSHOT_MISMATCH", "SALES_RESERVATION_MISMATCH");
	}
}
