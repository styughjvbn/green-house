package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupLedgerCoverageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class OrchidGroupLedgerWriterGuardTest {

	@Test
	void comparesSemanticVersionsAndRequiresExactMatchForOpaqueVersions() {
		assertThat(OrchidGroupLedgerWriterVersion.satisfiesMinimum("1.3.0", "1.2.9")).isTrue();
		assertThat(OrchidGroupLedgerWriterVersion.satisfiesMinimum("1.2.8", "1.2.9")).isFalse();
		assertThat(OrchidGroupLedgerWriterVersion.satisfiesMinimum("release-a", "release-a")).isTrue();
		assertThat(OrchidGroupLedgerWriterVersion.satisfiesMinimum("release-b", "release-a")).isFalse();
		assertThat(OrchidGroupLedgerWriterVersion.satisfiesMinimum("1.2.9-rc1", "1.2.9")).isFalse();
	}

	@Test
	void rejectsOlderWritersWhenCoverageIsActive() {
		var repository = activeCoverageRepository("2.0.0");
		var groups = mock(OrchidGroupRepository.class);
		assertThatThrownBy(() -> new OrchidGroupLedgerWriterStartupGuard(repository,
				new OrchidGroupLedgerWriterProperties("1.9.0"), groups)
			.run(mock(ApplicationArguments.class))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("최소 버전");
		assertThatCode(() -> new OrchidGroupLedgerWriterStartupGuard(repository,
				new OrchidGroupLedgerWriterProperties("2.0.0"), groups)
			.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
	}

	@Test
	void rejectsIncompleteCutover() {
		for (boolean started : new boolean[] { false, true }) {
			assertThatThrownBy(
					() -> new OrchidGroupLedgerWriterStartupGuard(preparingCoverageRepository("2.0.0", started),
							new OrchidGroupLedgerWriterProperties("2.0.0"), mock(OrchidGroupRepository.class))
						.run(mock(ApplicationArguments.class)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("PREPARING");
		}
	}

	@Test
	void rejectsUnmigratedGroupsAndAllowsAnEmptyDatabase() {
		var groups = mock(OrchidGroupRepository.class);
		var guard = new OrchidGroupLedgerWriterStartupGuard(mock(OrchidGroupLedgerCoverageRepository.class),
				new OrchidGroupLedgerWriterProperties("2.0.0"), groups);
		assertThatCode(() -> guard.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
		when(groups.existsByStateRevisionIsNull()).thenReturn(true);
		assertThatThrownBy(() -> guard.run(mock(ApplicationArguments.class))).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("state-chain");
	}

	@Test
	void rejectsActivationWithoutCompleteStateChainImport() {
		OrchidGroupLedgerCoverage coverage = coverage("1.2.0");
		coverage.startImport(Instant.parse("2026-08-20T00:00:00Z"));

		assertThatThrownBy(() -> coverage.activate(Instant.parse("2026-08-20T00:01:00Z"), 0, "a".repeat(64)))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("state-chain manifest");
	}

	private OrchidGroupLedgerCoverageRepository activeCoverageRepository(String minimumWriterVersion) {
		OrchidGroupLedgerCoverage coverage = coverage(minimumWriterVersion);
		coverage.startImport(Instant.parse("2026-08-20T00:00:00Z"));
		coverage.claimImport("b".repeat(64));
		coverage.activate(Instant.parse("2026-08-20T00:01:00Z"), 0, "a".repeat(64));
		OrchidGroupLedgerCoverageRepository repository = mock(OrchidGroupLedgerCoverageRepository.class);
		when(repository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE)).thenReturn(Optional.of(coverage));
		return repository;
	}

	private OrchidGroupLedgerCoverageRepository preparingCoverageRepository(String minimumWriterVersion,
			boolean importStarted) {
		OrchidGroupLedgerCoverage coverage = coverage(minimumWriterVersion);
		if (importStarted) {
			coverage.startImport(Instant.parse("2026-08-20T00:00:00Z"));
		}
		OrchidGroupLedgerCoverageRepository repository = mock(OrchidGroupLedgerCoverageRepository.class);
		when(repository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.PREPARING)).thenReturn(Optional.of(coverage));
		return repository;
	}

	private OrchidGroupLedgerCoverage coverage(String minimumWriterVersion) {
		return new OrchidGroupLedgerCoverage(UUID.randomUUID(), 1, 1, LocalDate.of(2026, 8, 20), minimumWriterVersion);
	}

}
