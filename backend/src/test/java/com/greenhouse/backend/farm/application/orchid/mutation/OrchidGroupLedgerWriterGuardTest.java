package com.greenhouse.backend.farm.application.orchid.mutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverage;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupLedgerCoverageStatus;
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
	void rejectsLegacyOrOlderWritersWhenCoverageIsActive() {
		OrchidGroupLedgerCoverageRepository repository = activeCoverageRepository("1.2.0");
		ApplicationArguments arguments = mock(ApplicationArguments.class);

		assertThatThrownBy(() -> new OrchidGroupLedgerWriterStartupGuard(
				repository,
				new OrchidGroupLedgerWriterProperties(OrchidGroupLedgerWriterMode.LEGACY, "1.2.0"))
				.run(arguments))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ENGINE");
		assertThatThrownBy(() -> new OrchidGroupLedgerWriterStartupGuard(
				repository,
				new OrchidGroupLedgerWriterProperties(OrchidGroupLedgerWriterMode.ENGINE, "1.1.9"))
				.run(arguments))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("최소 버전");
		assertThatCode(() -> new OrchidGroupLedgerWriterStartupGuard(
				repository,
				new OrchidGroupLedgerWriterProperties(OrchidGroupLedgerWriterMode.ENGINE, "1.2.1"))
				.run(arguments))
				.doesNotThrowAnyException();
	}

	private OrchidGroupLedgerCoverageRepository activeCoverageRepository(String minimumWriterVersion) {
		OrchidGroupLedgerCoverage coverage = new OrchidGroupLedgerCoverage(
				UUID.randomUUID(), 1, 1, LocalDate.of(2026, 8, 20), minimumWriterVersion);
		coverage.startBaseline(Instant.parse("2026-08-20T00:00:00Z"));
		coverage.activate(Instant.parse("2026-08-20T00:01:00Z"), 0, "a".repeat(64));
		OrchidGroupLedgerCoverageRepository repository = mock(OrchidGroupLedgerCoverageRepository.class);
		when(repository.findFirstByStatus(OrchidGroupLedgerCoverageStatus.ACTIVE))
				.thenReturn(Optional.of(coverage));
		return repository;
	}
}
