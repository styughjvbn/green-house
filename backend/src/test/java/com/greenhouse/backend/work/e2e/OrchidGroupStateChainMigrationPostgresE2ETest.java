package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerCutoverService;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupLedgerReconciliationStage;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationManifest;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationService;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryKind;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupStateSnapshot;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
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
class OrchidGroupStateChainMigrationPostgresE2ETest extends WorkE2ETestBase {

	private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 20);
	private static final String WRITER_VERSION = "1.0.0";
	private static final String MANIFEST_FINGERPRINT = "a".repeat(64);
	private static final long DELETED_GROUP_ID = 999_999L;

	@Autowired private WorkTestDataSeeder seeder;
	@Autowired private OrchidGroupRepository orchidGroupRepository;
	@Autowired private OrchidGroupStateChainMigrationService migrationService;
	@Autowired private OrchidGroupLedgerCutoverService cutoverService;
	@Autowired private JdbcTemplate jdbcTemplate;

	private WorkTestDataSeeder.ContractScenario scenario;

	@BeforeEach
	void setUp() {
		seeder.reset();
		scenario = seeder.seedContractScenario();
	}

	@Test
	void appliesConsolidatedFinalMutationSchemaWithoutTransitionTables() {
		assertThat(jdbcTemplate.queryForList("""
				SELECT version || ':' || description
				FROM flyway_schema_history
				WHERE success = TRUE
				  AND version::INTEGER > 20
				ORDER BY installed_rank
				""", String.class))
				.containsExactly(
						"21:add orchid group mutation engine",
						"22:enforce orchid group mutation write fence",
						"23:normalize legacy orchid group pot sizes",
						"24:allocate farm reference codes");

		assertThat(jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name IN (
				      'orchid_group_history_migration_runs',
				      'orchid_group_shadow_comparisons'
				  )
				""", Long.class)).isZero();

		assertThat(jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'orchid_group_mutation_entries'
				  AND column_name = 'migration_run_id'
				""", Long.class)).isZero();

		assertThat(jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM information_schema.table_constraints constraint_info
				JOIN information_schema.key_column_usage column_info
				  ON column_info.constraint_catalog = constraint_info.constraint_catalog
				 AND column_info.constraint_schema = constraint_info.constraint_schema
				 AND column_info.constraint_name = constraint_info.constraint_name
				WHERE constraint_info.table_schema = 'public'
				  AND constraint_info.table_name = 'orchid_group_mutation_entries'
				  AND constraint_info.constraint_type = 'FOREIGN KEY'
				  AND column_info.column_name = 'orchid_group_id'
				""", Long.class)).isZero();
	}

	@Test
	void importsCompleteChainsReplaysIdempotentlyAndActivatesCoverage() {
		UUID cutoverKey = UUID.randomUUID();
		OrchidGroupStateSnapshot current = OrchidGroupStateSnapshot.from(
				orchidGroupRepository.findDetailById(scenario.orchidGroupId()).orElseThrow());
		OrchidGroupStateSnapshot observed = withQuantity(current, 88);
		OrchidGroupStateSnapshot deleted = withQuantity(current, 12);
		OrchidGroupStateChainMigrationManifest manifest = manifest(
				scenario.orchidGroupId(), observed, current, deleted);

		var validated = migrationService.validate(
				cutoverKey, BUSINESS_DATE, WRITER_VERSION, MANIFEST_FINGERPRINT, manifest);

		assertThat(validated.applied()).isFalse();
		assertThat(validated.reconciliation().stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.PRE_BASELINE);
		assertThat(validated.reconciliation().ready()).isTrue();

		var imported = migrationService.importManifest(
				cutoverKey, BUSINESS_DATE, WRITER_VERSION, MANIFEST_FINGERPRINT, manifest);

		assertThat(imported.applied()).isTrue();
		assertThat(imported.importedMutationCount()).isEqualTo(4);
		assertThat(imported.replayedMutationCount()).isZero();
		assertThat(imported.currentGroupCount()).isOne();
		assertThat(imported.deletedGroupCount()).isOne();
		assertThat(imported.reconciliation().stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.BASELINE_PREPARING);
		assertThat(imported.reconciliation().ready()).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT state_revision FROM orchid_groups WHERE id = ?",
				Long.class,
				scenario.orchidGroupId())).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries WHERE orchid_group_id = ?",
				Long.class,
				DELETED_GROUP_ID)).isEqualTo(2L);

		var replayed = migrationService.importManifest(
				cutoverKey, BUSINESS_DATE, WRITER_VERSION, MANIFEST_FINGERPRINT, manifest);

		assertThat(replayed.importedMutationCount()).isZero();
		assertThat(replayed.replayedMutationCount()).isEqualTo(4);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutations", Long.class)).isEqualTo(4L);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM orchid_group_mutation_entries", Long.class)).isEqualTo(4L);

		var cutover = cutoverService.execute(new OrchidGroupLedgerCutoverCommand(
				cutoverKey, BUSINESS_DATE, WRITER_VERSION, WRITER_VERSION, true));

		assertThat(cutover.activated()).isTrue();
		assertThat(cutover.reconciliation().stage())
				.isEqualTo(OrchidGroupLedgerReconciliationStage.ACTIVE);
		assertThat(cutover.reconciliation().ready()).isTrue();

		var activeReplay = migrationService.importManifest(
				cutoverKey, BUSINESS_DATE, WRITER_VERSION, MANIFEST_FINGERPRINT, manifest);
		assertThat(activeReplay.importedMutationCount()).isZero();
		assertThat(activeReplay.replayedMutationCount()).isEqualTo(4);
		assertThat(activeReplay.reconciliation().ready()).isTrue();
	}

	private OrchidGroupStateChainMigrationManifest manifest(
			Long currentGroupId,
			OrchidGroupStateSnapshot observed,
			OrchidGroupStateSnapshot current,
			OrchidGroupStateSnapshot deleted) {
		return new OrchidGroupStateChainMigrationManifest(
				2,
				"orchid_state_chain_manifest_normalizer",
				true,
				List.of(),
				Map.of(),
				Map.of(),
				List.of(
						mutation(
								"current-baseline",
								OrchidGroupMutationType.BASELINE_IMPORT,
								"EARLIEST_TRUSTWORTHY_BASELINE",
								"current-observation",
								entry(currentGroupId, OrchidGroupMutationEntryKind.BASELINE,
										OrchidGroupMutationEntryRole.AFFECTED, null, 0L, null, observed)),
						mutation(
								"current-correction",
								OrchidGroupMutationType.CORRECTION,
								"AUDIT_EVENT",
								"current-correction",
								entry(currentGroupId, OrchidGroupMutationEntryKind.CHANGE,
										OrchidGroupMutationEntryRole.AFFECTED, 0L, 1L, observed, current)),
						mutation(
								"deleted-create",
								OrchidGroupMutationType.CREATE,
								"AUDIT_EVENT",
								"deleted-create",
								entry(DELETED_GROUP_ID, OrchidGroupMutationEntryKind.CREATE,
										OrchidGroupMutationEntryRole.RESULT, null, 1L, null, deleted)),
						mutation(
								"deleted-terminal",
								OrchidGroupMutationType.DELETE,
								"AUDIT_EVENT",
								"deleted-terminal",
								entry(DELETED_GROUP_ID, OrchidGroupMutationEntryKind.DELETE,
										OrchidGroupMutationEntryRole.SOURCE, 1L, 2L, deleted, null))));
	}

	private OrchidGroupStateChainMigrationManifest.Mutation mutation(
			String key,
			OrchidGroupMutationType type,
			String sourceType,
			String sourceReference,
			OrchidGroupStateChainMigrationManifest.Entry entry) {
		return new OrchidGroupStateChainMigrationManifest.Mutation(
				key,
				type,
				sourceType,
				sourceReference,
				Instant.parse("2026-08-20T00:00:00Z"),
				BUSINESS_DATE,
				"PostgreSQL state-chain rehearsal",
				Map.of(),
				List.of(entry));
	}

	private OrchidGroupStateChainMigrationManifest.Entry entry(
			Long groupId,
			OrchidGroupMutationEntryKind kind,
			OrchidGroupMutationEntryRole role,
			Long revisionBefore,
			Long revisionAfter,
			OrchidGroupStateSnapshot before,
			OrchidGroupStateSnapshot after) {
		return new OrchidGroupStateChainMigrationManifest.Entry(
				groupId, kind, role, revisionBefore, revisionAfter, before, after);
	}

	private OrchidGroupStateSnapshot withQuantity(OrchidGroupStateSnapshot source, int quantity) {
		return new OrchidGroupStateSnapshot(
				quantity,
				source.reservedQuantity(),
				source.status(),
				source.bedZoneId(),
				source.sortOrder(),
				source.startPosition(),
				source.endPosition(),
				source.varietyId(),
				source.genus(),
				source.varietyName(),
				source.ageYear(),
				source.potSizeCode(),
				source.placementType(),
				source.trayCount(),
				source.splitPlacementAllowed(),
				source.inboundRecordId(),
				source.memo());
	}
}
