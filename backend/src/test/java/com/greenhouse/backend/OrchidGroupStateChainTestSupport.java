package com.greenhouse.backend;

import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationManifest;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupStateChainMigrationResult;
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

public final class OrchidGroupStateChainTestSupport {

	private static final String FINGERPRINT = "b".repeat(64);

	private OrchidGroupStateChainTestSupport() {
	}

	public static OrchidGroupStateChainMigrationResult importCurrentGroups(
			OrchidGroupStateChainMigrationService migrationService,
			OrchidGroupRepository orchidGroupRepository,
			UUID cutoverKey,
			LocalDate businessDate,
			String writerVersion) {
		var ids = orchidGroupRepository.findAll().stream().map(group -> group.getId()).toList();
		var entries = orchidGroupRepository.findDetailsByIds(ids).stream()
				.map(group -> new OrchidGroupStateChainMigrationManifest.Entry(
						group.getId(),
						OrchidGroupMutationEntryKind.BASELINE,
						OrchidGroupMutationEntryRole.AFFECTED,
						null,
						0L,
						null,
						OrchidGroupStateSnapshot.from(group)))
				.toList();
		var mutation = new OrchidGroupStateChainMigrationManifest.Mutation(
				"test-baseline-" + cutoverKey,
				OrchidGroupMutationType.BASELINE_IMPORT,
				"EARLIEST_TRUSTWORTHY_BASELINE",
				"test-database",
				Instant.parse("2026-08-20T00:00:00Z"),
				businessDate,
				"Test complete state-chain",
				Map.of(),
				entries);
		var manifest = new OrchidGroupStateChainMigrationManifest(
				2,
				"orchid_state_chain_manifest_normalizer",
				true,
				List.of(),
				Map.of(),
				Map.of(),
				List.of(mutation));
		return migrationService.importManifest(
				cutoverKey, businessDate, writerVersion, FINGERPRINT, manifest);
	}
}
