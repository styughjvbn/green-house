package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.mutation.BaselineOrchidGroupsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerPreparationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationStage;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
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
import org.springframework.transaction.support.TransactionTemplate;

@Tag("work-e2e")
class OrchidGroupLedgerReconciliationPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupLedgerPreparationService preparationService;
	@Autowired private OrchidGroupLedgerCutoverService cutoverService;
	@Autowired private OrchidGroupLedgerReconciliationService reconciliationService;
	@Autowired private OrchidGroupMutationEngine mutationEngine;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private TransactionTemplate transactionTemplate;

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

	@Test
	void activatesAfterAnEngineCreatedGroupWhileCoverageIsPreparing() {
		UUID cutoverKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, businessDate, "1.0.0", "1.0.0", false));
		Long varietyId = jdbcTemplate.queryForObject(
				"SELECT variety_id FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId());

		var created = transactionTemplate.execute(status -> mutationEngine.create(
				new CreateOrchidGroupMutationCommand(
				new OrchidGroupMutationSource(
						OrchidGroupMutationSourceDomain.WORK,
						"WORK_EFFECT",
						"post-baseline-work",
						"EXECUTION:post-baseline-create",
						UUID.randomUUID()),
				scenario.bedZoneId(),
				new OrchidGroupMutationDetails(
						varietyId,
						10,
						"3.5치",
						2,
						"정상",
						"POT",
						null,
						false,
						new BigDecimal("6"),
						new BigDecimal("7"),
						null),
				businessDate.plusDays(1),
				"PREPARING smoke test 생성")));

		var activated = cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, businessDate, "1.0.0", "1.0.0", true));
		var report = activated.reconciliation();

		assertThat(created.entries()).singleElement().satisfies(entry ->
				assertThat(entry.entryKind()).isEqualTo(OrchidGroupMutationEntryKind.CREATE));
		assertThat(report.stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.ACTIVE);
		assertThat(report.orchidGroupCount()).isEqualTo(2);
		assertThat(report.baselineGroupCount()).isEqualTo(1);
		assertThat(activated.activated()).isTrue();
		assertThat(report.ready()).isTrue();
		assertThat(report.issues()).isEmpty();
	}
}
