package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupHistoricalEvidenceInput;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupHistoricalMutationInput;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupHistoryMigrationPlanCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupHistoryMigrationService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationStage;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationFingerprint;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupHistoricalEvidenceKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupHistoricalEvidenceQuality;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("work-e2e")
class OrchidGroupHistoricalMigrationPostgresE2ETest extends WorkE2ETestBase {

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupHistoryMigrationService migrationService;
	@Autowired private OrchidGroupLedgerCutoverService cutoverService;
	@Autowired private OrchidGroupMutationFingerprint mutationFingerprint;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
	}

	@Test
	void importsAttestedEvidenceIdempotentlyWithoutChangingCurrentStateThenAllowsCutover() {
		UUID runKey = UUID.randomUUID();
		Instant sourceCutoff = Instant.parse("2026-08-21T00:00:00Z");
		LocalDate businessDate = LocalDate.of(2026, 8, 21);
		var plan = new OrchidGroupHistoryMigrationPlanCommand(
				runKey,
				sourceCutoff,
				fingerprint("backup"),
				fingerprint("manifest"),
				businessDate,
				Map.of("ORCHID_GROUP", 1L),
				Map.of("MUTATIONS", 1L, "EVIDENCE", 1L));
		var candidate = correctionCandidate(scenario.orchidGroupId(), 88, 100);

		Long firstRunId = migrationService.plan(plan);
		assertThat(migrationService.plan(plan)).isEqualTo(firstRunId);
		migrationService.start(runKey);

		var imported = migrationService.importBatch(runKey, List.of(candidate));
		var replayed = migrationService.importBatch(runKey, List.of(candidate));

		assertThat(imported.importedMutations()).isEqualTo(1);
		assertThat(imported.replayedMutations()).isZero();
		assertThat(replayed.importedMutations()).isZero();
		assertThat(replayed.replayedMutations()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT quantity FROM orchid_groups WHERE id = ?",
				Integer.class,
				scenario.orchidGroupId())).isEqualTo(100);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId())).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_historical_evidence",
				Long.class)).isEqualTo(1);

		assertThatThrownBy(() -> migrationService.importBatch(
				runKey,
				List.of(correctionCandidate(scenario.orchidGroupId(), 87, 100))))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("payload");

		assertThat(migrationService.completeImport(runKey))
				.containsEntry("MUTATIONS", 1L)
				.containsEntry("EVIDENCE", 1L);
		assertThat(migrationService.verify(runKey)).containsEntry("ready", true);
		assertThat(migrationService.importBatch(runKey, List.of(candidate)).replayedMutations())
				.isEqualTo(1);

		UUID cutoverKey = UUID.randomUUID();
		var cutover = cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, businessDate, "1.0.0", "1.0.0", false));

		assertThat(cutover.reconciliation().stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING);
		assertThat(cutover.reconciliation().ready()).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_historical_evidence",
				Long.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries",
				Long.class)).isEqualTo(1);
	}

	@Test
	void blocksCutoverWhileAHistoricalMigrationRunIsIncomplete() {
		UUID runKey = UUID.randomUUID();
		LocalDate businessDate = LocalDate.of(2026, 8, 21);
		migrationService.plan(new OrchidGroupHistoryMigrationPlanCommand(
				runKey,
				Instant.parse("2026-08-21T00:00:00Z"),
				fingerprint("backup"),
				fingerprint("manifest"),
				businessDate,
				Map.of("ORCHID_GROUP", 1L),
				Map.of("MUTATIONS", 1L, "EVIDENCE", 1L)));

		assertThatThrownBy(() -> cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				UUID.randomUUID(), businessDate, "1.0.0", "1.0.0", false)))
				.isInstanceOf(ConflictException.class)
				.hasMessageContaining("완료되지 않은 historical migration");
	}

	private OrchidGroupHistoricalMutationInput correctionCandidate(
			Long groupId,
			int beforeQuantity,
			int afterQuantity) {
		return new OrchidGroupHistoricalMutationInput(
				OrchidGroupMutationType.CORRECTION,
				new OrchidGroupMutationSource(
						OrchidGroupMutationSourceDomain.MIGRATION,
						"OPERATOR_ATTESTATION",
						groupId.toString(),
						"QUANTITY:+12",
						UUID.nameUUIDFromBytes(("OPERATOR_ATTESTATION:" + groupId)
								.getBytes(StandardCharsets.UTF_8))),
				Instant.parse("2026-08-08T23:49:32Z"),
				LocalDate.of(2026, 8, 9),
				"운영자 확인 과거 수량 보정",
				List.of(new OrchidGroupHistoricalEvidenceInput(
						groupId,
						OrchidGroupMutationEntryRole.AFFECTED,
						OrchidGroupHistoricalEvidenceKind.CHANGE,
						OrchidGroupHistoricalEvidenceQuality.ATTESTED,
						List.of("quantity"),
						Map.of("quantity", beforeQuantity),
						Map.of("quantity", afterQuantity),
						Map.of("quantityDelta", afterQuantity - beforeQuantity),
						Map.of("attestationReference", "OWNER_CONFIRMATION:2026-08-21"))));
	}

	private String fingerprint(String value) {
		return mutationFingerprint.calculate(value);
	}
}
